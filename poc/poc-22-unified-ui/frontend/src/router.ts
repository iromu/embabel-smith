import { createRouter, createWebHistory } from 'vue-router'

import RunPane from './panes/RunPane.vue'
import AuthorPane from './panes/AuthorPane.vue'
import ComposePane from './panes/ComposePane.vue'
import RetrievePane from './panes/RetrievePane.vue'
import EvalPane from './panes/EvalPane.vue'
import LifecyclePane from './panes/LifecyclePane.vue'
import SecurityPane from './panes/SecurityPane.vue'
import TrustPane from './panes/TrustPane.vue'
import RemotePane from './panes/RemotePane.vue'
import ScalePane from './panes/ScalePane.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/run' },
    { path: '/run', name: 'run', component: RunPane },
    { path: '/author', name: 'author', component: AuthorPane },
    { path: '/compose', name: 'compose', component: ComposePane },
    { path: '/retrieve', name: 'retrieve', component: RetrievePane },
    { path: '/eval', name: 'eval', component: EvalPane },
    { path: '/lifecycle', name: 'lifecycle', component: LifecyclePane },
    { path: '/security', name: 'security', component: SecurityPane },
    { path: '/trust', name: 'trust', component: TrustPane },
    { path: '/remote', name: 'remote', component: RemotePane },
    { path: '/scale', name: 'scale', component: ScalePane },
  ],
})
