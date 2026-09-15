const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const html=fs.readFileSync(path.join(__dirname,'../../catalog-site/index.html'),'utf8');
const script=html.match(/<script>([\s\S]*?)<\/script>/)[1];
class Element {
  constructor(tag){this.tag=tag;this.children=[];this.handlers={};this.value='';this.textContent='';}
  append(...items){this.children.push(...items);}
  replaceChildren(...items){this.children=[...items];}
  addEventListener(event,handler){this.handlers[event]=handler;}
}
async function page(items,fail=false){
  const elements=Object.fromEntries(['search','platform','games','status'].map(k=>['#'+k,new Element(k)]));
  const document={querySelector:k=>elements[k],createElement:tag=>new Element(tag)};
  vm.runInNewContext(script,{document,URL,fetch:async()=>{if(fail)throw Error('offline');return {ok:true,json:async()=>({version:1,items})};}});
  await new Promise(resolve=>setImmediate(resolve));return elements;
}
(async()=>{
 const data=[{label:'<img src=x onerror=alert(1)> Ação',platform:'NES',category:'demo',license:'CC0',description:'Puzzle',tags:['test'],image:'assets/missing.png',publicDownload:'https://example.org/demo'}, {label:'Second',platform:'GBA',category:'homebrew',license:'CC0'}, {label:'Commercial',category:'commercial',license:'restricted'}];
 const e=await page(data);
 assert.equal(e['#games'].children.length,2,'Commercial category must not render');
 assert.equal(e['#games'].children[0].children[1].children[1].textContent,data[0].label,'Titles must remain literal text');
 e['#search'].value='acao';e['#search'].handlers.input();assert.equal(e['#games'].children.length,1,'Search must ignore accents');
 e['#search'].value='';e['#platform'].value='GBA';e['#platform'].handlers.change();assert.equal(e['#games'].children.length,1,'Platform filter must narrow results');
 e['#platform'].value='';e['#search'].handlers.input();const cover=e['#games'].children[0].children[0];cover.children[0].handlers.error();assert.equal(cover.children[0].textContent,'Capa não disponível','Broken cover must have a fallback');
 assert.match((await page([]))['#status'].textContent,/ainda não tem/,'Empty catalog message missing');
 assert.match((await page([],true))['#status'].textContent,/Não foi possível/,'Offline error message missing');
 console.log('PASS: gallery rendering, literal text, accent search, platform filter, missing cover, empty and offline states');
})().catch(error=>{console.error(error);process.exitCode=1;});
