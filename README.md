# 🛒 ShopWave

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Version](https://img.shields.io/badge/version-1.0.0-green.svg)

A full-stack e-commerce web application inspired by Amazon, built with a modern Java/React tech stack. ShopWave allows users to browse products, manage a cart, place orders, and more — all in a clean, responsive storefront experience.

---

## 🚀 Tech Stack

**Frontend**
- [React](https://reactjs.org/) — Component-based UI library
- [TypeScript](https://www.typescriptlang.org/) — Strongly typed JavaScript

**Backend**
- [Java](https://www.java.com/) — Core backend language
- [Spring Boot](https://spring.io/projects/spring-boot) — REST API framework

**Database**
- [PostgreSQL](https://www.postgresql.org/) — Relational database

---

## ✨ Features

- 🔍 Browse and search products
- 🛒 Add items to cart and manage quantities
- 💳 Checkout and order placement
- 👤 User registration and authentication
- 📦 Order history and tracking
- 🛠️ Admin product management

---

## 📁 Project Structure

```
ShopWave/
├── client/                  # React + TypeScript frontend
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── hooks/
│   │   ├── services/        # API calls
│   │   └── types/
│   └── package.json
│
└── server/                  # Spring Boot backend
    ├── src/main/java/
    │   └── com/shopwave/
    │       ├── controllers/
    │       ├── services/
    │       ├── repositories/
    │       ├── models/
    │       └── config/
    └── pom.xml
```

---

## ⚙️ Getting Started

### Prerequisites

- Node.js (v18+) & npm
- Java 17+
- Maven
- PostgreSQL (v14+)

### 1. Clone the Repository

```bash
git clone https://github.com/your-username/shopwave.git
cd shopwave
```

### 2. Database Setup

```sql
CREATE DATABASE shopwave;
```

Update `server/src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/shopwave
spring.datasource.username=your_username
spring.datasource.password=your_password
spring.jpa.hibernate.ddl-auto=update
```

### 3. Run the Backend

```bash
cd server
mvn spring-boot:run
```

The API will be available at `http://localhost:8080`.

### 4. Run the Frontend

```bash
cd client
npm install
npm run dev
```

The app will be available at `http://localhost:5173`.

---

## 🔌 API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/auth/register` | Register a new user |
| `POST` | `/api/auth/login` | Login and receive JWT |
| `GET` | `/api/products` | Get all products |
| `GET` | `/api/products/:id` | Get a product by ID |
| `GET` | `/api/cart` | Get current user's cart |
| `POST` | `/api/cart/items` | Add item to cart |
| `DELETE` | `/api/cart/items/:id` | Remove item from cart |
| `POST` | `/api/orders` | Place an order |
| `GET` | `/api/orders` | Get user's order history |

---

## 🧪 Running Tests

**Backend:**
```bash
cd server
mvn test
```

**Frontend:**
```bash
cd client
npm run test
```

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Commit your changes (`git commit -m 'Add your feature'`)
4. Push to the branch (`git push origin feature/your-feature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
