import { createSSRApp } from 'vue'
import App from './App.vue'
import 'virtual:uno.css'
import './styles/tokens.scss'

export function createApp() {
  const app = createSSRApp(App)
  return {
    app,
  }
}
