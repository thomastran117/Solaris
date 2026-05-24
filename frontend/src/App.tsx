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
import BrowsePage from "./pages/BrowsePage";
import CompanyPage from "./pages/CompanyPage";
import ProductPage from "./pages/ProductPage";
import SavedListsPage from "./pages/SavedListsPage";
import SavedListDetailPage from "./pages/SavedListDetailPage";
import PublicSavedListPage from "./pages/PublicSavedListPage";
import AdminProductsPage from "./pages/admin/AdminProductsPage";
import AdminProductEditor from "./pages/admin/AdminProductEditor";
import AdminCollectionsPage from "./pages/admin/AdminCollectionsPage";
import AdminCollectionEditor from "./pages/admin/AdminCollectionEditor";
import AdminCollectionProductsPage from "./pages/admin/AdminCollectionProductsPage";
import AdminTeamPage from "./pages/admin/AdminTeamPage";
import AdminBulkImportPage from "./pages/admin/AdminBulkImportPage";
import ReviewModerationPage from "./pages/admin/ReviewModerationPage";
import InviteAcceptPage from "./pages/InviteAcceptPage";
import CollectionPage from "./pages/CollectionPage";
import AccountPage from "./pages/AccountPage";
import LoyaltyPage from "./pages/LoyaltyPage";
import AdminLoyaltyPage from "./pages/admin/AdminLoyaltyPage";
import FeedbackPage from "./pages/FeedbackPage";
import AdminFeedbackPage from "./pages/admin/AdminFeedbackPage";
import FeedbackButton from "./components/feedback/FeedbackButton";
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
            <Route path="/browse" element={<BrowsePage />} />
            <Route path="/c/:id" element={<CompanyPage />} />
            <Route path="/products/:id" element={<ProductPage />} />
            <Route path="/lists" element={<SavedListsPage />} />
            <Route path="/lists/:id" element={<SavedListDetailPage />} />
            <Route path="/lists/public/:slug" element={<PublicSavedListPage />} />
            <Route path="/admin/products" element={<AdminProductsPage />} />
            <Route path="/admin/products/new" element={<AdminProductEditor />} />
            <Route path="/admin/products/:id" element={<AdminProductEditor />} />
            <Route path="/admin/collections" element={<AdminCollectionsPage />} />
            <Route path="/admin/collections/new" element={<AdminCollectionEditor />} />
            <Route path="/admin/collections/:id/edit" element={<AdminCollectionEditor />} />
            <Route path="/admin/collections/:id/products" element={<AdminCollectionProductsPage />} />
            <Route path="/admin/team" element={<AdminTeamPage />} />
            <Route path="/admin/bulk-import" element={<AdminBulkImportPage />} />
            <Route path="/admin/reviews/reports" element={<ReviewModerationPage />} />
            <Route path="/invite/accept/:token" element={<InviteAcceptPage />} />
            <Route path="/collections/:slug" element={<CollectionPage />} />
            <Route path="/account" element={<AccountPage />} />
            <Route path="/loyalty" element={<LoyaltyPage />} />
            <Route path="/feedback" element={<FeedbackPage />} />
            <Route path="/admin/loyalty" element={<AdminLoyaltyPage />} />
            <Route path="/admin/feedback" element={<AdminFeedbackPage />} />
            <Route path="/hello" element={<HelloTestPage />} />
            <Route path="/auth/google" element={<AuthCallback />} />
          </Routes>
        </div>
        <FeedbackButton />
        <Footer />
      </Router>
    </>
  );
}

export default App;
