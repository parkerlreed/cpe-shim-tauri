// SPDX-License-Identifier: GPL-3.0-or-later
package local.omniremote;

import android.app.*;
import android.content.*;
import android.hardware.usb.*;
import android.net.Uri;
import android.os.*;
import android.util.*;
import android.webkit.*;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import org.json.*;
import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private static final String PERMISSION="local.omniremote.irtest.USB_PERMISSION";
    private WebView web;
    private UsbManager usb;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled=new AtomicBoolean(false), busy=new AtomicBoolean(false);
    private UsbDevice pending;
    private int[] pendingTransmit;
    private int pendingFrequency;
    private boolean exitRequested=false;
    private final BroadcastReceiver permissions=new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if(!PERMISSION.equals(intent.getAction())) return;
            UsbDevice device=pending; pending=null;
            int[] transmission=pendingTransmit;pendingTransmit=null;
            if(device!=null && intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false)) {
                if(transmission!=null) send(device,pendingFrequency,transmission);else capture(device);
            } else if(transmission!=null) transmitResult("USB permission was denied");
            else complete(null,"USB permission was denied");
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        usb=(UsbManager)getSystemService(USB_SERVICE);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(permissions,new IntentFilter(PERMISSION),Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(permissions,new IntentFilter(PERMISSION));
        web=new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        WebView.setWebContentsDebuggingEnabled(true);
        web.addJavascriptInterface(new NativeBridge(),"AndroidIR");
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onConsoleMessage(ConsoleMessage m) {Log.i("OmniRemoteWeb",m.message()+" @"+m.lineNumber());return true;}
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri uri=req.getUrl();
                if(!"omniremote.local".equals(uri.getHost())) return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
                String path=uri.getPath();if(path==null) return null;path=path.substring(1);
                if(path.contains("..")) return null;
                try {
                    InputStream input;
                    File saved=new File(getFilesDir(),"session.img");
                    if(path.equals("session.img") && saved.exists()) input=new FileInputStream(saved);
                    else input=getAssets().open(path);
                    String mime=path.endsWith(".wasm")?"application/wasm":path.endsWith(".js")?"application/javascript":path.endsWith(".html")?"text/html":path.endsWith(".svg")?"image/svg+xml":"application/octet-stream";
                    return new WebResourceResponse(mime,"UTF-8",input);
                } catch(IOException e) {return new WebResourceResponse("text/plain","UTF-8",404,"Not found",null,new ByteArrayInputStream(new byte[0]));}
            }
        });
        setContentView(web);
        hideStatusBar();
        if(Build.VERSION.SDK_INT>=33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::saveAndExit);
        web.loadUrl("https://omniremote.local/index.html");
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }
    private void hideStatusBar() {
        if(Build.VERSION.SDK_INT>=30) {
            WindowInsetsController controller=getWindow().getInsetsController();
            if(controller!=null) {
                controller.hide(WindowInsets.Type.statusBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus)hideStatusBar();
    }
    private void saveAndExit() {
        if(exitRequested)return;
        exitRequested=true;
        cancelled.set(true);
        web.evaluateJavascript("typeof window.requestExit === 'function' && window.requestExit()",result->{
            if("false".equals(result)||"null".equals(result))finish();
        });
    }
    @Override public void onBackPressed() {saveAndExit();}
    private final class NativeBridge {
        @JavascriptInterface public void transmit(int frequency,String encodedPattern) {
            final int[] pattern;
            try {JSONArray values=new JSONArray(encodedPattern);if(values.length()>100000)throw new Exception("IR pattern is too long");pattern=new int[values.length()];for(int i=0;i<pattern.length;i++)pattern[i]=values.getInt(i);}
            catch(Exception e){transmitResult("Invalid IR transmit pattern");return;}
            runOnUiThread(()->{
                if(busy.get()||pending!=null){transmitResult("USB learning or permission request is in progress");return;}
                UsbDevice device=null;
                for(UsbDevice candidate:usb.getDeviceList().values())if(UsbLearner.supported(candidate)){device=candidate;break;}
                if(device==null){transmitResult("Connect the USB IR dongle to this phone");return;}
                if(usb.hasPermission(device))send(device,frequency,pattern);
                else {
                    pending=device;pendingTransmit=pattern;pendingFrequency=frequency;
                    PendingIntent intent=PendingIntent.getBroadcast(MainActivity.this,0,new Intent(PERMISSION).setPackage(getPackageName()),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);
                    usb.requestPermission(device,intent);
                }
            });
        }
        @JavascriptInterface public void learn() {
            if(!busy.compareAndSet(false,true)) return;
            cancelled.set(false);
            runOnUiThread(()->{
                UsbDevice device=null;
                for(UsbDevice candidate:usb.getDeviceList().values()) if(UsbLearner.supported(candidate)){device=candidate;break;}
                if(device==null){complete(null,"Connect the USB IR dongle to this phone");return;}
                if(usb.hasPermission(device)) capture(device);
                else {
                    pending=device;
                    PendingIntent intent=PendingIntent.getBroadcast(MainActivity.this,0,new Intent(PERMISSION).setPackage(getPackageName()),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);
                    usb.requestPermission(device,intent);
                }
            });
        }
        @JavascriptInterface public void cancel() {cancelled.set(true);}
        @JavascriptInterface public void saveAborted() {
            web.post(()->{exitRequested=false;web.evaluateJavascript("window.saveFailed && window.saveFailed()",null);});
        }
        @JavascriptInterface public void save(String encoded) {
            try {
                byte[] data=Base64.decode(encoded,Base64.DEFAULT);
                File target=new File(getFilesDir(),"session.img"), temp=new File(getFilesDir(),"session.tmp");
                Files.write(temp.toPath(),data);
                Files.move(temp.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Log.i("OmniRemoteIR","Palm session saved: "+data.length+" bytes");
                web.post(()->{
                    if(exitRequested)finish();
                    else web.evaluateJavascript("window.saved && window.saved()",null);
                });
            }catch(Exception e){
                Log.e("OmniRemoteIR","Save failed",e);
                web.post(()->{exitRequested=false;web.evaluateJavascript("window.saveFailed && window.saveFailed()",null);});
            }
        }
    }
    private void capture(UsbDevice device) {
        worker.execute(()->{
            try{complete(UsbLearner.capture(usb,device,cancelled),null);}
            catch(Exception e){Log.e("OmniRemoteIR","Learning failed",e);complete(null,e.getMessage());}
        });
    }
    private void send(UsbDevice device,int frequency,int[] pattern) {
        worker.execute(()->{
            try{UsbTransmitter.send(usb,device,frequency,pattern);transmitResult(null);}
            catch(Exception e){Log.e("OmniRemoteIR","Transmit failed",e);transmitResult(e.getMessage());}
        });
    }
    private void transmitResult(String error) {
        String argument=error==null?"null":JSONObject.quote(error);
        web.post(()->web.evaluateJavascript("window.irTransmitComplete("+argument+")",null));
    }
    private void complete(int[] timings,String error) {
        busy.set(false);
        JSONObject result=new JSONObject();
        try {result.put("error",error==null?JSONObject.NULL:error);if(timings!=null){JSONArray a=new JSONArray();for(int v:timings)a.put(v);result.put("timings",a);}}
        catch(JSONException e){throw new RuntimeException(e);}
        String script="window.irCaptureComplete("+result+")";
        web.post(()->web.evaluateJavascript(script,null));
    }
    @Override protected void onDestroy() {
        cancelled.set(true);unregisterReceiver(permissions);worker.shutdown();web.destroy();super.onDestroy();
    }
}
