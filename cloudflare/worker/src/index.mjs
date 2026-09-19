import { verifyAccessJwt } from './access.mjs';
import { adminApi } from './admin-api.mjs';
import { routeAdminPairings, routeDeviceApi } from './device-api.mjs';
import { routeImporterApi } from './importer-api.mjs';
import { ApiError, errorResponse, jsonResponse } from './errors.mjs';
export { LibraryCoordinator } from './coordinator.mjs';


export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (url.pathname === '/api/health' && request.method === 'GET') return jsonResponse({ ok: true, service: 'jogos-retro-cloud' });
    if (url.pathname === '/pair/' || url.pathname === '/pair') {
      const pairId = url.searchParams.get('pair');
      const adminUrl = new URL('/admin/', url.origin);
      if (pairId) adminUrl.searchParams.set('pair', pairId);
      return Response.redirect(adminUrl.toString(), 302);
    }
    if ((url.pathname === '/admin' || url.pathname === '/admin/') && request.method === 'GET' && url.searchParams.has('pair')) {
      const adminUrl = new URL('/admin/', url.origin);
      adminUrl.searchParams.set('pair', url.searchParams.get('pair'));
      if (url.pathname === '/admin') return Response.redirect(adminUrl.toString(), 302);
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
      if (assetUrl.pathname === '/admin') assetUrl.pathname = '/admin/';
      return env.ASSETS.fetch(new Request(assetUrl, request));
    }
    return errorResponse(new ApiError(404, 'not_found', 'This route does not exist.'));
  }
};
