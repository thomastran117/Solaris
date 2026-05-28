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
import AdminBundlesPage from "./pages/admin/AdminBundlesPage";
import AdminKitsPage from "./pages/admin/AdminKitsPage";
import AdminKitEditor from "./pages/admin/AdminKitEditor";
import KitPage from "./pages/KitPage";
import AdminReportsPage from "./pages/admin/AdminReportsPage";
import InviteAcceptPage from "./pages/InviteAcceptPage";
import CollectionPage from "./pages/CollectionPage";
import AccountPage from "./pages/AccountPage";
import LoyaltyPage from "./pages/LoyaltyPage";
import AdminLoyaltyPage from "./pages/admin/AdminLoyaltyPage";
import FeedbackPage from "./pages/FeedbackPage";
import AdminFeedbackPage from "./pages/admin/AdminFeedbackPage";
import OrdersPage from "./pages/OrdersPage";
import OrderDetailPage from "./pages/OrderDetailPage";
import CompanyOrdersPage from "./pages/admin/CompanyOrdersPage";
import CompanyOrderDetailPage from "./pages/admin/CompanyOrderDetailPage";
import DriverDispatchPage from "./pages/admin/DriverDispatchPage";
import FeedPage from "./pages/FeedPage";
import FollowingPage from "./pages/FollowingPage";
import AdminAnnouncementsPage from "./pages/admin/AdminAnnouncementsPage";
import FeedbackButton from "./components/feedback/FeedbackButton";
import Footer from "./components/Footer";
import Sidebar from "./components/Sidebar";
import ProtectedRoute from "./components/auth/ProtectedRoute";

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
            {/* Public routes */}
            <Route path="/browse" element={<BrowsePage />} />
            <Route path="/c/:id" element={<CompanyPage />} />
            <Route path="/products/:id" element={<ProductPage />} />
            <Route path="/lists/public/:slug" element={<PublicSavedListPage />} />
            <Route path="/collections/:slug" element={<CollectionPage />} />
            <Route path="/kits/:companyId/:kitId" element={<KitPage />} />
            <Route path="/invite/accept/:token" element={<InviteAcceptPage />} />
            <Route path="/hello" element={<HelloTestPage />} />
            <Route path="/auth/google" element={<AuthCallback />} />

            {/* Protected routes — redirect to /login if unauthenticated */}
            <Route element={<ProtectedRoute />}>
              <Route path="/feed" element={<FeedPage />} />
              <Route path="/following" element={<FollowingPage />} />
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/lists" element={<SavedListsPage />} />
              <Route path="/lists/:id" element={<SavedListDetailPage />} />
              <Route path="/account" element={<AccountPage />} />
              <Route path="/loyalty" element={<LoyaltyPage />} />
              <Route path="/feedback" element={<FeedbackPage />} />
              <Route path="/orders" element={<OrdersPage />} />
              <Route path="/orders/:id" element={<OrderDetailPage />} />
              <Route path="/admin/products" element={<AdminProductsPage />} />
              <Route path="/admin/bundles" element={<AdminBundlesPage />} />
              <Route path="/admin/kits" element={<AdminKitsPage />} />
              <Route path="/admin/kits/new" element={<AdminKitEditor />} />
              <Route path="/admin/kits/:id/edit" element={<AdminKitEditor />} />
              <Route path="/admin/products/new" element={<AdminProductEditor />} />
              <Route path="/admin/products/:id" element={<AdminProductEditor />} />
              <Route path="/admin/collections" element={<AdminCollectionsPage />} />
              <Route path="/admin/collections/new" element={<AdminCollectionEditor />} />
              <Route path="/admin/collections/:id/edit" element={<AdminCollectionEditor />} />
              <Route path="/admin/collections/:id/products" element={<AdminCollectionProductsPage />} />
              <Route path="/admin/team" element={<AdminTeamPage />} />
              <Route path="/admin/bulk-import" element={<AdminBulkImportPage />} />
              <Route path="/admin/reports" element={<AdminReportsPage />} />
              <Route path="/admin/loyalty" element={<AdminLoyaltyPage />} />
              <Route path="/admin/feedback" element={<AdminFeedbackPage />} />
              <Route path="/admin/announcements" element={<AdminAnnouncementsPage />} />
              <Route path="/admin/orders" element={<CompanyOrdersPage />} />
              <Route path="/admin/orders/:orderId" element={<CompanyOrderDetailPage />} />
              <Route path="/admin/drivers" element={<DriverDispatchPage />} />
            </Route>
          </Routes>
        </div>
        <FeedbackButton />
        <Footer />
      </Router>
    </>
  );
}

export default App;
