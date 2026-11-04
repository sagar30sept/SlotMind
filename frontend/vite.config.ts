import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/appointments": "http://localhost:8080",
      "/providers": "http://localhost:8080",
      "/slots": "http://localhost:8080",
      "/suggestions": "http://localhost:8080",
      "/waitlist": "http://localhost:8080",
    },
  },
});
