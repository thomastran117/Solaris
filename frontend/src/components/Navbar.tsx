import React, { useState } from "react";
import { FaSearch, FaUserCircle } from "react-icons/fa";
import { useNavigate, NavLink } from "react-router-dom";
import { useSelector, useDispatch } from "react-redux";
import type { RootState, AppDispatch } from "../stores";
import { clearCredentials } from "../stores/authSlice";
import "../styles/navbar.css";

export default function Navbar() {
  const navigate = useNavigate();
  const dispatch = useDispatch<AppDispatch>();

  const { accessToken, email } = useSelector((state: RootState) => state.auth);

  const username = email ?? null;

  const [userDropdownOpen, setUserDropdownOpen] = useState(false);
  const [moreDropdownOpen, setMoreDropdownOpen] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const [moreTimeoutId, setMoreTimeoutId] = useState<NodeJS.Timeout | null>(
    null
  );
  const [userTimeoutId, setUserTimeoutId] = useState<NodeJS.Timeout | null>(
    null
  );

  const handleMoreEnter = () => {
    if (moreTimeoutId) {
      clearTimeout(moreTimeoutId);
      setMoreTimeoutId(null);
    }
    setMoreDropdownOpen(true);
  };

  const handleMoreLeave = () => {
    const timeout = setTimeout(() => {
      setMoreDropdownOpen(false);
    }, 200);
    setMoreTimeoutId(timeout);
  };

  const handleUserEnter = () => {
    if (userTimeoutId) {
      clearTimeout(userTimeoutId);
      setUserTimeoutId(null);
    }
    setUserDropdownOpen(true);
  };

  const handleUserLeave = () => {
    const timeout = setTimeout(() => {
      setUserDropdownOpen(false);
    }, 200);
    setUserTimeoutId(timeout);
  };

  const handleLogout = () => {
    setUserDropdownOpen(false);

    dispatch(clearCredentials());

    // await api.post("/auth/logout");

    navigate("/auth");
  };

  const navLinkClass = ({ isActive }: { isActive: boolean }) =>
    `nav-link px-3 py-2 rounded ${isActive ? "active font-semibold" : ""}`;

  return (
    <nav className="bg-gradient-to-r from-[#0b1221]/95 via-[#16213d]/95 to-[#1d3a75]/95 backdrop-blur-sm text-white shadow-lg sticky top-0 z-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between h-16 items-center">
          {/* Brand */}
          <NavLink
            to="/"
            className="flex items-center text-2xl font-semibold text-white"
          >
            <i className="fas fa-ghost mr-2 text-xl"></i> ShopWave
          </NavLink>

          {/* Mobile toggle */}
          <button
            className="lg:hidden text-gray-300 hover:text-white focus:outline-none"
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          >
            <svg
              className="w-6 h-6"
              fill="none"
              stroke="currentColor"
              strokeWidth={2}
              viewBox="0 0 24 24"
            >
              {mobileMenuOpen ? (
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M6 18L18 6M6 6l12 12"
                />
              ) : (
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M4 6h16M4 12h16M4 18h16"
                />
              )}
            </svg>
          </button>

          {/* Nav links */}
          <div
            className={`lg:flex lg:items-center ${
              mobileMenuOpen ? "block" : "hidden"
            } w-full lg:w-auto`}
          >
            <div className="flex flex-col lg:flex-row lg:space-x-4 mt-4 lg:mt-0">
              <NavLink to="/" end className={navLinkClass}>
                Home
              </NavLink>
              <NavLink to="/about" className={navLinkClass}>
                About
              </NavLink>
              <NavLink to="/browse" className={navLinkClass}>
                Browse
              </NavLink>

              {/* More Dropdown */}
              <div
                className="relative"
                onMouseEnter={handleMoreEnter}
                onMouseLeave={handleMoreLeave}
              >
                <button className="px-3 py-2 rounded text-gray-300 hover:text-white flex items-center transition">
                  More
                  <svg
                    className={`w-4 h-4 ml-1 transition-transform duration-300 ${
                      moreDropdownOpen ? "rotate-180" : ""
                    }`}
                    fill="currentColor"
                    viewBox="0 0 20 20"
                  >
                    <path d="M5.23 7.21a.75.75 0 011.06.02L10 10.94l3.71-3.71a.75.75 0 111.06 1.06l-4.24 4.24a.75.75 0 01-1.06 0L5.21 8.29a.75.75 0 01.02-1.08z" />
                  </svg>
                </button>
                {moreDropdownOpen && (
                  <div className="absolute mt-2 w-44 bg-white text-black rounded-lg shadow-xl z-20 border border-gray-200">
                    {[
                      { label: "Profiles", to: "/profiles" },
                      { label: "Hello", to: "/hello" },
                      { label: "About", to: "/about" },
                      { label: "Contact", to: "/contact", divider: true },
                    ].map((item) => (
                      <React.Fragment key={item.label}>
                        {item.divider && (
                          <hr className="my-1 border-gray-200" />
                        )}
                        <NavLink
                          to={item.to}
                          className="block px-4 py-2 text-sm hover:bg-blue-50 hover:text-blue-700 transition rounded-md mx-1"
                        >
                          {item.label}
                        </NavLink>
                      </React.Fragment>
                    ))}
                  </div>
                )}
              </div>
            </div>

            {/* Search bar */}
            <form className="flex items-center mt-4 lg:mt-0 lg:ml-6">
              <div className="relative flex rounded overflow-hidden border border-gray-600">
                <input
                  type="text"
                  placeholder="Search..."
                  className="bg-gray-800 text-white px-3 py-1 focus:outline-none w-36 sm:w-48"
                />
                <button
                  type="submit"
                  className="bg-blue-600 hover:bg-blue-700 px-3 py-1"
                >
                  <FaSearch />
                </button>
              </div>
            </form>

            {/* User/Login */}
            <div className="relative mt-4 lg:mt-0 lg:ml-6">
              {accessToken ? (
                <div
                  onMouseEnter={handleUserEnter}
                  onMouseLeave={handleUserLeave}
                  className="cursor-pointer"
                >
                  <div className="flex items-center gap-1 text-gray-300 hover:text-blue-400">
                    <FaUserCircle />
                    <span>{username}</span>
                    <svg
                      className={`w-4 h-4 ml-1 transition-transform duration-300 ${
                        userDropdownOpen ? "rotate-180" : ""
                      }`}
                      fill="currentColor"
                      viewBox="0 0 20 20"
                    >
                      <path d="M5.23 7.21a.75.75 0 011.06.02L10 10.94l3.71-3.71a.75.75 0 111.06 1.06l-4.24 4.24a.75.75 0 01-1.06 0L5.21 8.29a.75.75 0 01.02-1.08z" />
                    </svg>
                  </div>
                  {userDropdownOpen && (
                    <div className="absolute right-0 mt-2 w-44 bg-white text-black rounded-lg shadow-xl z-20 border border-gray-200">
                      <NavLink
                        to="/profile"
                        className="block px-4 py-2 text-sm hover:bg-blue-50 hover:text-blue-700 transition rounded-md mx-1"
                      >
                        My Profile
                      </NavLink>
                      <NavLink
                        to="/orders"
                        className="block px-4 py-2 text-sm hover:bg-blue-50 hover:text-blue-700 transition rounded-md mx-1"
                      >
                        My Orders
                      </NavLink>
                      <hr className="my-1 border-gray-200" />
                      <button
                        onClick={handleLogout}
                        className="block w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-red-50 hover:text-red-700 transition rounded-md mx-1"
                      >
                        Logout
                      </button>
                    </div>
                  )}
                </div>
              ) : (
                <NavLink
                  to="/login"
                  className="flex items-center text-gray-300 hover:text-blue-400"
                >
                  <FaUserCircle className="mr-1" />
                  Login
                </NavLink>
              )}
            </div>
          </div>
        </div>
      </div>
    </nav>
  );
}
