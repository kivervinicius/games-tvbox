import { verifyAccessJwt } from './access.mjs';
import { adminApi } from './admin-api.mjs';
import { routeAdminPairings, routeDeviceApi } from './device-api.mjs';
import { routeImporterApi } from './importer-api.mjs';
import { ApiError, errorResponse, jsonResponse } from './errors.mjs';

function pairingApprovalPage() {
  return new Response(`<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Aprovar esta TV</title>
<style>body{margin:0;background:#050b18;color:#edf5ff;font:16px system-ui,sans-serif;display:grid;min-height:100vh;place-items:center}.card{box-sizing:border-box;width:min(560px,92vw);padding:32px;border:1px solid #27e5f3;border-radius:18px;background:#0d1b31}h1{margin-top:0}label,input,button{display:block;width:100%;box-sizing:border-box}input{margin:8px 0 18px;padding:13px;border:1px solid #39d8ea;border-radius:9px;background:#091426;color:#fff}button{padding:14px;border:0;border-radius:9px;background:#27e5f3;color:#06101d;font-weight:700;cursor:pointer}#status{min-height:24px;margin-top:18px;color:#a8c7ec}</style></head>
<body><main class="card"><h1>Aprovar esta TV</h1><p>Digite o código mostrado na TV. Ele vale uma única vez e expira em dez minutos.</p><label>Nome da TV<input id="name" maxlength="80" value="Sala"></label><label>Código de pareamento<input id="code" autocomplete="one-time-code" autofocus></label><button id="approve">Aprovar TV</button><p id="status" aria-live="polite"></p></main>
<script>const pairId=new URLSearchParams(location.search).get('pair')||'';const status=document.getElementById('status');document.getElementById('approve').onclick=async()=>{const code=document.getElementById('code').value.trim();if(!pairId||!code){status.textContent='Informe o código mostrado na TV.';return}status.textContent='Aprovando…';try{const r=await fetch('/api/device/pair/approve',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({pairId,code,name:document.getElementById('name').value.trim()})});const data=await r.json();status.textContent=r.ok?'TV aprovada. Volte à tela da TV para concluir.':(data.error&&data.error.message)||'Não foi possível aprovar.'}catch(e){status.textContent='Falha de conexão. Tente novamente.'}}</script></body></html>`, {
    headers: { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' }
  });
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (url.pathname === '/api/health' && request.method === 'GET') return jsonResponse({ ok: true, service: 'jogos-retro-cloud' });
    if (url.pathname === '/pair/' && request.method === 'GET') return pairingApprovalPage();
    if ((url.pathname === '/admin' || url.pathname === '/admin/') && request.method === 'GET' && url.searchParams.has('pair')) {
      const approval = new URL('/pair/', url.origin);
      approval.searchParams.set('pair', url.searchParams.get('pair'));
      return Response.redirect(approval.toString(), 302);
    }
    if (url.pathname.startsWith('/api/admin/')) {
      try {
        await verifyAccessJwt(request, env);
        const pairingResponse = await routeAdminPairings(request, env);
        if (pairingResponse) return pairingResponse;
        return await adminApi(request, env);
      } catch (error) { return errorResponse(error); }
    }
    if (url.pathname.startsWith('/api/device/')) {
      try { return await routeDeviceApi(request, env); }
      catch (error) { return errorResponse(error); }
    }
    if (url.pathname.startsWith('/api/importer/')) {
      try { return await routeImporterApi(request, env); }
      catch (error) { return errorResponse(error); }
    }
    if (env.ASSETS) {
      const assetUrl = new URL(request.url);
      // Cloudflare Assets resolves directory indexes itself. Rewriting /admin/
      // to index.html causes an endless 307 redirect in local and production.
      if (assetUrl.pathname === '/admin') assetUrl.pathname = '/admin/';
      return env.ASSETS.fetch(new Request(assetUrl, request));
    }
    return errorResponse(new ApiError(404, 'not_found', 'This route does not exist.'));
  }
};
