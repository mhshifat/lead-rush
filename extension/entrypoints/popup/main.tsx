import { createRoot } from 'react-dom/client'
import { App } from './App'
import '@/assets/theme.css'

const container = document.getElementById('root')!
createRoot(container).render(<App />)
