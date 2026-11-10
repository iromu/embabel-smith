import { createApp } from 'vue'
import { Quasar, Notify } from 'quasar'
import 'quasar/src/css/index.sass'
import '@quasar/extras/material-icons/material-icons.css'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import './assets/global.css'

import App from './App.vue'
import { router } from './router'

const app = createApp(App)
app.use(router)
app.use(Quasar, { plugins: { Notify } })
app.mount('#app')

// The studio is a dark-mode product. Quasar defaults the body to `body--light` on
// mount, so force the dark variant after mount (synchronously, before paint) to
// drive Quasar's dark component surfaces (fields, tables, badges, buttons).
document.body.classList.remove('body--light')
document.body.classList.add('body--dark')
