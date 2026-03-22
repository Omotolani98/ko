import { Routes, Route } from 'react-router-dom';
import Layout from './components/Layout';
import ArchitecturePage from './pages/ArchitecturePage';
import ApiExplorerPage from './pages/ApiExplorerPage';
import ServiceCatalogPage from './pages/ServiceCatalogPage';
import TracesPage from './pages/TracesPage';
import DatabasePage from './pages/DatabasePage';

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<ArchitecturePage />} />
        <Route path="/api-explorer" element={<ApiExplorerPage />} />
        <Route path="/services" element={<ServiceCatalogPage />} />
        <Route path="/services/:name" element={<ServiceCatalogPage />} />
        <Route path="/traces" element={<TracesPage />} />
        <Route path="/databases" element={<DatabasePage />} />
      </Route>
    </Routes>
  );
}
