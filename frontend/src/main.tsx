import { StrictMode } from 'react';
import { render } from 'react-dom';
import { BrowserRouter } from 'react-router-dom';
import { App } from './App';
// Component CSS defines defaults; the official corporate theme must override them.
import '@alfalab/core-components-themes/corp.css';
import './styles.css';

const root = document.getElementById('root');
if (!root) throw new Error('Root element is missing');
render(<StrictMode><BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}><App /></BrowserRouter></StrictMode>, root);
