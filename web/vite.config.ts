import { defineConfig, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

/**
 * Pre-renders the landing page into index.html at build time (SEO and link previews), adds JSON-LD,
 * and the optional Google Search Console tag (GOOGLE_SITE_VERIFICATION). The app itself is served
 * from app.html, which search engines are told not to index.
 */
function prerenderLanding(): Plugin {
  let rendered = { html: '', jsonLd: '' };
  return {
    name: 'meld-prerender-landing',
    apply: 'build',
    async buildStart() {
      const { createServer } = await import('vite');
      const server = await createServer({
        configFile: false,
        plugins: [react()],
        server: { middlewareMode: true, hmr: false },
        appType: 'custom',
        logLevel: 'error',
      });
      try {
        const mod = await server.ssrLoadModule('/src/prerender.tsx');
        rendered = mod.renderLanding();
      } finally {
        await server.close();
      }
    },
    transformIndexHtml: {
      order: 'post',
      handler(html, ctx) {
        if (!ctx.filename.endsWith('index.html')) return html;
        const verify = (process.env.GOOGLE_SITE_VERIFICATION || '').replace(/[^A-Za-z0-9_-]/g, '');
        return html
          .replace('<div id="root"></div>', `<div id="root">${rendered.html}</div>`)
          .replace(
            '</head>',
            `  <script type="application/ld+json">${rendered.jsonLd}</script>\n` +
              (verify ? `    <meta name="google-site-verification" content="${verify}" />\n` : '') +
              '  </head>',
          );
      },
    },
  };
}

export default defineConfig({
  build: {
    rollupOptions: { input: { main: 'index.html', app: 'app.html' } },
  },
  plugins: [
    react(),
    prerenderLanding(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.png', 'icons/apple-touch-icon.png', 'fonts/*.ttf'],
      manifest: {
        name: 'Meld — decide together',
        short_name: 'Meld',
        description: 'Free group polls, ranked choice and time slots. No sign-up, works offline.',
        theme_color: '#0B0B0D',
        background_color: '#0B0B0D',
        display: 'standalone',
        start_url: '/app',
        scope: '/',
        icons: [
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: '/icons/maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        // App shell + fonts are precached; every app route falls back to app.html offline.
        globPatterns: ['**/*.{js,css,html,png,ttf,svg,webmanifest}'],
        navigateFallback: '/app.html',
        globIgnores: ['google*.html'],
        navigateFallbackDenylist: [/^\/\.well-known\//, /^\/api\//, /^\/cal\//, /^\/google[0-9a-f]+\.html$/],
      },
    }),
  ],
});
