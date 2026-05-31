import { BrowserRouter as Router, Routes, Route } from "react-router-dom";
import "./App.css";
import LoginPage from "./pages/LoginPage";
import TermsAndConditionPage from "./pages/TermsAndConditionPage";
import PrivacyPage from "./pages/PrivacyPage";
import NavbarComponent from "./components/Navbar";
import HomePage from "./pages/HomePage";
import AuthCallback from "./pages/AuthCallback";
import HelloTestPage from "./pages/HelloTestPage";
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
            <Route path="/dashboard" element={<LoginPage />} />
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
