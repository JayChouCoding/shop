import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import App from './AppShell.vue';
import router from './router';
import './styles.css';

createApp(App).use(router).use(ElementPlus).mount('#app');
