import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // 监听所有网络接口，允许局域网访问（http://<本机IP>:5173）
    host: '0.0.0.0',
    // 允许的 Host 白名单（防 DNS rebinding；放行内网穿透/局域网域名）
    allowedHosts: [
      'localhost',
      '127.0.0.1',
      '192.168.8.212',
      '.linkease.net', // 内网穿透域名及其子域（p0-0-4-xxx.linkease.net）
      '.local',
    ],
    proxy: {
      // 开发环境：/api -> 网关 8080（避免 CORS）
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        // 网关路由可能带前缀，保留原始路径即可
        // rewrite: (p) => p.replace(/^\/api/, '/api'),
      },
      '/internal': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
