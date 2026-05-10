import { BrowserRouter as Router, Routes, Route } from "react-router-dom";
import "./App.css";
import LoginPage from "./pages/LoginPage";
import TermsAndConditionPage from "./pages/TermsAndConditionPage";
import PrivacyPage from "./pages/PrivacyPage";
import NavbarComponent from "./components/Navbar";
import HomePage from "./pages/HomePage";
import AuthCallback from "./pages/AuthCallback";
import HelloTestPage from "./pages/HelloTestPage";
import DashboardPage from "./pages/DashboardPage";
import ComparisonPage from "./pages/ComparisonPage";
import BrowsePage from "./pages/BrowsePage";
import ProductPage from "./pages/ProductPage";
import SavedListsPage from "./pages/SavedListsPage";
import SavedListDetailPage from "./pages/SavedListDetailPage";
import PublicSavedListPage from "./pages/PublicSavedListPage";
import AdminProductsPage from "./pages/admin/AdminProductsPage";
import AdminProductEditor from "./pages/admin/AdminProductEditor";
import Footer from "./components/Footer";
import Sidebar from "./components/Sidebar";

function App() {
  return (
    <>
      <Router>
        <NavbarComponent />
        <div className="min-h-screen overflow-y-auto overflow-x-hidden">
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route
              path="/terms-and-conditions"
              element={
                <TermsAndConditionPage
                  companyName="ShopWave"
                  contactEmail="support@shopwave.com"
                  lastUpdated="August 22, 2025"
                />
              }
            />
            <Route
              path="/privacy"
              element={
                <PrivacyPage
                  companyName="ShopWave"
                  contactEmail="support@shopwave.com"
                  dpoEmail="dpo@shopwave.com"
                  lastUpdated="August 22, 2025"
                />
              }
            />
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/compare" element={<ComparisonPage />} />
            <Route path="/browse" element={<BrowsePage />} />
            <Route path="/products/:id" element={<ProductPage />} />
            <Route path="/lists" element={<SavedListsPage />} />
            <Route path="/lists/:id" element={<SavedListDetailPage />} />
            <Route path="/lists/public/:slug" element={<PublicSavedListPage />} />
            <Route path="/admin/products" element={<AdminProductsPage />} />
            <Route path="/admin/products/new" element={<AdminProductEditor />} />
            <Route path="/admin/products/:id" element={<AdminProductEditor />} />
            <Route path="/hello" element={<HelloTestPage />} />
            <Route path="/auth/google" element={<AuthCallback />} />
          </Routes>
        </div>
        <Footer />
      </Router>
    </>
  );
}

export default App;
