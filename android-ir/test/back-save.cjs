const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const html = fs.readFileSync(`${__dirname}/../app/src/main/assets/index.html`, 'utf8');
const page = html.match(/<script>\s*([\s\S]*?)<\/script>/)[1];
new vm.Script(page);
function fixture() {
 const calls = [], events = {};
 const element = {getContext:()=>({}), addEventListener(){}, getBoundingClientRect:()=>({left:0,top:0,width:160,height:221})};
 const context = {
  console, Uint8Array, clearTimeout(){}, setTimeout(){}, requestAnimationFrame:fn=>fn(),
  btoa:s=>Buffer.from(s,'binary').toString('base64'),
  document:{querySelector:()=>element,querySelectorAll:()=>[],addEventListener:(name,fn)=>events[name]=fn},
  AndroidIR:{save:s=>calls.push(['save',s]),cancel:()=>calls.push(['cancel']),saveAborted:()=>calls.push(['abort'])},
  testCpu:{SaveState:()=>true,IsSuspended:()=>false,IsIrLearnPending:()=>true,GetSuspendContext:()=>({Cancel:()=>calls.push(['cancelGuest'])})},
  testModule:{getPointer:p=>p,HEAPU8:new Uint8Array([1,2,3]),destroy(){}}
 };
 for(const method of ['GetRomPtr','GetRomSize','GetMemoryPtr','GetMemorySize','GetSavestatePtr','GetSavestateSize'])context.testCpu[method]=()=>0;
 context.testModule.SessionImage=class{
  SetDeviceId(){} SetRomImage(){} SetMemoryImage(){} SetSavestate(){} Serialize(){return true;} GetSerializedImage(){return 0;} GetSerializedImageSize(){return 3;}
 };
 context.window=context;vm.createContext(context);vm.runInContext(page.slice(0,page.indexOf('(async()=>{')),context);
 vm.runInContext('M=testModule;cp=testCpu;',context);
 return {context,calls,events,run:s=>vm.runInContext(s,context)};
}
{
 const f=fixture();assert.equal(f.context.requestExit(),false);assert.deepEqual(f.calls,[]);
 f.run('started=true');assert.equal(f.context.requestExit(),true);assert.deepEqual(f.calls,[['save','AQID']]);
 f.context.document.hidden=true;f.events.visibilitychange();assert.equal(f.calls.length,1);
}
{
 const f=fixture();f.run('started=true;learning=true');f.context.requestExit();assert.deepEqual(f.calls,[['cancel']]);
 f.context.irCaptureComplete({error:'Capture cancelled'});assert.deepEqual(f.calls,[['cancel'],['cancelGuest'],['save','AQID']]);
}
{
 const f=fixture();f.context.testCpu.SaveState=()=>false;f.run('started=true');f.context.requestExit();assert.deepEqual(f.calls,[['abort']]);
}
{
 const f=fixture();f.run('started=true');const points=[];f.context.testCpu.QueuePenMove=(x,y)=>points.push([x,y]);
 f.run('pen({clientX:80,clientY:100});pen({clientX:80,clientY:190});');assert.deepEqual(points,[[80,100],[80,189]]);
}
console.log('Back saves before exit; cancelled capture saves after guest resume; failed saves abort exit; Graffiti coordinates preserved.');
