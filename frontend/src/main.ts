import './assets/main.css'
import '@teeetime/teaz-pro-ui/style.css'
import 'katex/dist/katex.min.css'
import './editor/content.css'

import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router'
import { useAuth } from './auth'

// Login is optional: init() only finds out whether someone is logged in.
useAuth()
  .init()
  .catch((error) => console.error('Login check failed, continuing as guest', error))
  .then(() => createApp(App).use(router).mount('#app'))
