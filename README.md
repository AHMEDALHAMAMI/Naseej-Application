<div align="center">

<img src="assets/images/NaseejLogo.png" width="150" alt="NaseejLogo.png">

**Neighborhood demand platform powered by AI**

منصة يطلب فيها سكان الحي المحلات والخدمات اللي تنقصهم، ويستخدم المستثمرون هذا الطلب الموثّق لفتح المشروع في المكان الصح

![Java](https://img.shields.io/badge/Java-17+-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3-6DB33F?logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security-JWT-6DB33F?logo=springsecurity&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-4479A1?logo=mysql&logoColor=white)
![Claude](https://img.shields.io/badge/Claude_AI-D97757?logo=anthropic&logoColor=white)
![n8n](https://img.shields.io/badge/n8n-EA4B71?logo=n8n&logoColor=white)
![Moyasar](https://img.shields.io/badge/Moyasar-Payments-1D4ED8)
![Google Places](https://img.shields.io/badge/Google_Places-4285F4?logo=googlemaps&logoColor=white)
![WhatsApp](https://img.shields.io/badge/WhatsApp-25D366?logo=whatsapp&logoColor=white)
![Gmail](https://img.shields.io/badge/Gmail-EA4335?logo=gmail&logoColor=white)

</div>

---

Naseej lets neighborhood residents request the shops and services they're missing, and investors use that verified demand to choose the right project and location. AI screens every idea before it goes live, residents join for free as founding members, and investors unlock completed requests with a one-time fee or a success partnership, or buy a request early, before its votes complete, at a lower price. The pilot starts in Riyadh neighborhoods.

## 📑 Table of Contents

- [Features](#-features)
- [Tech Stack](#️-tech-stack)
- [Integrations](#-integrations)
- [n8n Workflows](#️-n8n-workflows)
- [Architecture](#️-architecture)
- [How Idea Screening Works](#-how-idea-screening-works)
- [Use Case Diagram](#use-case-diagram)
- [Class Diagram](#class-diagram)
- [Authentication & Roles](#-authentication--roles)
- [Revenue Model](#-revenue-model)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
- [Team](#-team)
- [Endpoints](#-endpoints)
- [Contact](#-contact)

## ✨ Features

- **Residents** post requests by text or voice in their own dialect, join existing requests for free as founding members, and see demand on a neighborhood map. A member counts only after email and residence verification.
- **Idea owners** get AI screening before publishing and an AI-drafted business model they can edit by chat.
- **Investors** search by neighborhood, capital or project name, read a short AI feasibility study, unlock opportunities through Moyasar or a success partnership, and sign contracts inside the platform.
- **Landlords** list vacant shops that appear inside nearby opportunities.
- **Everyone** gets WhatsApp and email alerts when a request completes, an idea is adopted or a business opens.

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17+, Spring Boot 3, Spring Data JPA, Jakarta Validation, Lombok |
| Security | Spring Security, JWT (access + refresh), Google OAuth2, BCrypt |
| Database | MySQL |
| Tools | n8n, Postman, Git & GitHub |

## 🔌 Integrations

| Service | Used for | How we integrated it |
|---|---|---|
| <img src="https://cdn.simpleicons.org/anthropic" width="18"> **Claude AI** | Request understanding, Idea Screening, Business Model, Feasibility Study | The server sends the idea, Google Places results and nearby published ideas to Claude. Every response comes back as JSON and is validated by the server before it is saved. |
| <img src="https://cdn.simpleicons.org/googlemaps" width="18"> **Google Places API** | Scarcity check, competitors | Finds businesses of the same type within a radius that fits the activity, then calculates the count and nearest competitor and compares them with the city average. |
| <img src="https://cdn.simpleicons.org/googlemaps" width="18"> **Google Maps** | Demand map | Shows neighborhoods colored by the number of votes for each activity. |
| <img src="https://cdn.simpleicons.org/google" width="18"> **Google OAuth2** | Sign-in | `oauth2Login()` signs the user in. New users are created with the `PENDING` role, sent to the membership page, then issued a JWT. |
| 💳 **Moyasar** | Payments and refunds | Moyasar's ready payment form handles Apple Pay, mada and cards, so card data never reaches our server. An opportunity is unlocked only after Moyasar's webhook confirms the payment, and refunds go through the Refund API. |
| <img src="https://cdn.simpleicons.org/n8n" width="18"> **n8n** | Automation | Spring Boot and Moyasar trigger n8n workflows through webhooks for screening, alerts, contracts, deadlines and digests. |
| <img src="https://cdn.simpleicons.org/whatsapp" width="18"> **WhatsApp** | Alerts | Sent by n8n: request completed, idea adopted, contract ready to sign, business opened. |
| <img src="https://cdn.simpleicons.org/gmail" width="18"> **Gmail** | OTP codes and contracts | Connected in n8n through an OAuth2 credential from the platform's official account, so emails arrive from "Naseej" and not from a person. |
| 🪪 **Nafath** | Investor verification | Investors must verify their identity through Nafath before they can access opportunities. |

Every AI result is validated by the server before it is stored or used in any payment step, and nothing is unlocked for an investor before Moyasar's webhook arrives. Moyasar keys are kept in environment variables.

## ⚙️ n8n Workflows

| Workflow | Trigger | Action |
|---|---|---|
| New idea | App webhook | Screen the idea and notify interested neighbors |
| Request completed | Vote threshold reached | Generate the feasibility study and alert investors |
| Payment confirmed | Moyasar webhook | Unlock the opportunity and issue the VAT invoice |
| Adoption follow-up | Investor adopts | Collect signatures, email the contract PDF, follow up every 2 weeks |
| Adoption expired | Not signed within 14 days, or not opened within 180 days (240 with the extension) | Reopen the opportunity and update the voters |
| Email OTP | Sign-up or login | Send the 6-digit code through Gmail |
| Weekly digest | Every Sunday | Send investors the top rising requests |

## 🏗️ Architecture

<p align="center">
  <a href="assets/docs/ArchitectureDiagram.svg">
    <img src="assets/docs/ArchitectureDiagram.svg" alt="Naseej's Architecture Diagram" width="100%">
  </a>
</p>

## 🔍 How Idea Screening Works

Before an idea is published, Claude extracts the activity and neighborhood, Google Places counts competitors within a radius that fits the activity (1 km for a supermarket, 3 km for a gym), and a semantic check looks for similar ideas nearby. The result is a score out of 100 and one decision:

| Decision | Meaning | Next step |
|---|---|---|
| `SUITABLE` | Clear gap, no matching idea | Build the business model, then publish |
| `SIMILAR_EXISTS` | A close idea is posted nearby | Join it, or publish and explain the difference |
| `SATURATED` | Competitors well above the city average | Suggest a scarcer activity in the same neighborhood |

**Vote target.** A bigger project risks more money, so it needs more neighbors behind it. When an idea is published its target is fixed from the business model's estimated capital: **20 votes + 1 vote for every 50,000 SAR** (middle of the range), rounded to the nearest 5, between 20 and 100 (40 when there's no estimate). A barber (120k–220k) needs 25, a café (380k–620k) 30, a supermarket (1M–1.8M) 50, a trampoline park (1.8M–3M) 70. Investors can buy before the target at a lower price; the success partnership opens at 100%.

## Use Case Diagram

<p align="center">
  <a href="assets/docs/UseCaseDiagram.svg">
    <img src="assets/docs/UseCaseDiagram.svg" alt="Naseej's Use Case Diagram" width="100%">
  </a>
</p>

## Class Diagram

<p align="center">
  <a href="assets/docs/ClassDiagram.svg">
    <img src="assets/docs/ClassDiagram.svg" alt="Naseej's Class Diagram" width="100%">
  </a>
</p>

## 🔐 Authentication & Roles

Login is stateless with JWT access and refresh tokens. Users sign up with Google or with email and password; email accounts verify with a 6-digit OTP (valid 5 minutes, locked after 5 wrong attempts), and investors verify with Nafath before accessing opportunities. All endpoints start with `/v1/api`, only sign-up and login are public, and role-specific actions are protected with `@PreAuthorize`.

| Role | Can |
|---|---|
| `PENDING` | Google user before choosing a membership |
| `RESIDENT` | Post ideas, join as a founding member, sign as idea owner |
| `INVESTOR` | Search, unlock and adopt opportunities, sign contracts |
| `LANDLORD` | List vacant shops |
| `ADMIN` | Manage users and opportunities, receive a copy of every contract |

## 💰 Revenue Model

Residents and idea owners use Naseej for free. Prices below are proposals for testing with the first investors.

| Source | Who pays | Proposed price |
|---|---|---|
| Opportunity access fee | Investor | 1,500 SAR + VAT per opportunity |
| Success partnership | Investor who doesn't pay upfront | 5% of net profit for 3 years after opening |
| Investor subscription | Active investors | 299 or 999 SAR per month |
| Vacant shop listing | Landlords | 199 SAR per month per shop |

## 📁 Project Structure

```
com.example.naseej
├── Api          # ApiException and ApiResponse
├── Config       # Security, OAuth2 and external service configs
├── Controller   # REST endpoints
├── DTOIN        # Request bodies with validation
├── DTOOUT       # Response bodies
├── Enums        # Role, IdeaStatus, ContractStatus and other enums
├── Entity        # JPA entities
├── Repository   # Database access
├── Security     # JwtAuthenticationFilter, AuthenticationSuccessHandler
└── Service      # Business logic
```

## 🚀 Getting Started

1. Clone the repository: `git clone https://github.com/<your-org>/Naseej.git`
2. Create a MySQL database and set the connection details in `src/main/resources/application.properties`.
3. Add the API keys as environment variables (below).
4. Import the n8n workflows and add a Gmail OAuth2 credential from the platform's official account.
5. Run `./mvnw spring-boot:run`. Use Moyasar's test keys and test cards during development.

<details>
<summary>Environment variables</summary>

```properties
claude.api.key=${CLAUDE_API_KEY}
claude.model=${CLAUDE_MODEL}
google.places.api.key=${GOOGLE_PLACES_API_KEY}
spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}
jwt.secret=${JWT_SECRET}
moyasar.secret.key=${MOYASAR_SECRET_KEY}
moyasar.publishable.key=${MOYASAR_PUBLISHABLE_KEY}
n8n.webhook.url=${N8N_WEBHOOK_URL}
```

</details>

<details>
<summary>Test accounts (development only)</summary>

| Role | Email | Password | Notes |
|---|---|---|---|
| Admin | `TODO` | `TODO` | Receives a copy of every contract |
| Resident | `TODO` | `TODO` | Residence verified, owns an idea |
| Resident | `TODO` | `TODO` | Founding member of that idea |
| Investor | `TODO` | `TODO` | Nafath verified |
| Investor | `TODO` | `TODO` | Not verified, can't access opportunities |
| Landlord | `TODO` | `TODO` | Has a vacant shop listed |

</details>

## 🌐 Web App

The website lives in `src/main/resources/static` and is served by the same Spring Boot app, so there is nothing extra to install or build.

1. Start the backend (`./mvnw spring-boot:run`).
2. Open **http://localhost:8080/api/v1/** (the pages share the `/api/v1` context path with the API).
3. In Google Cloud, the authorized redirect URI stays `http://localhost:8080/api/v1/login/oauth2/code/google`. After Google sign-in the browser is sent to `oauth-callback.html` with the tokens.
4. To try unlocking with a payment, set Moyasar **test** keys in `.env`; checkout runs in Moyasar's sandbox.

| Page | Who | What it does |
|---|---|---|
| `index.html` | everyone | Landing page, live numbers, map of open ideas near the visitor (asks for location) |
| `register.html` / `verify.html` | new users | Email sign-up in steps: account type → details → (residents) home address pin → email code. Investors only create the account; their preferences come next in onboarding |
| `choose-role.html` | Google sign-up | Step 1 membership, step 2 the resident's home address or the investor's last confirmation |
| `onboarding.html` | new investors | Three skippable questions after sign-up: budget (preset or custom range), fields (or "something else" in their own words), neighborhoods |
| `resident.html` | residents | Map around the resident's saved home with the voting radius, vote / view details (no vote counts). Residents vote from their saved addresses, never from the device location |
| `idea.html` | everyone logged in | Idea page; founder and investors also see votes and the business model. Investors get `idea-investor.js`, everyone else `idea-resident.js` |
| `propose.html` | residents, investors | Five steps: the idea → the place → why → AI check → review. A passed idea waits as a private draft until the author publishes it; similar ideas are rejected. Investors can then adopt their own idea |
| `investor.html` | investors | **For you**: the list and the map side by side. *Your fit* shows the opportunities that match the investor's preferences (ready-to-unlock first); *All* shows every opportunity with field and budget filters. Zoomed out, the map groups pins by city |
| `ask.html` | investors | **Ask AI**: a question in plain words → the filters the AI understood → matching opportunities |
| `unlock.html` | investors | One payment (Moyasar, AI-priced; lower before the votes complete) or 5% success partnership (at 100% of the votes); asks for the CR here if it's missing |
| `contract.html` | investors, founders | The contract after unlocking/adopting, in three steps: purchase → investor signs → active (the idea owner doesn't sign) |
| `projects.html` | investors, founders | Investors (`projects-investor.js`): two tabs, projects (status, deadline, progress) and their ideas. Residents (`projects-resident.js`): their ideas and drafts, and the projects that came from them |
| `account.html` | everyone | **Account** (from the avatar menu). Investors (`account-investor.js`): details, investment preferences, CR verification, WhatsApp alerts, language. Residents (`account-resident.js`): profile, password and up to two saved home addresses |
| `agreement.html` | investors, founders | A readable copy of a project's agreement |
| `admin.html` | admin | Dashboard charts, members, investor verification and neighborhoods |
| `styleguide.html` | developers | The design system: colors, type, buttons, forms, steps, AI blocks and notices |

Libraries load from CDNs: Leaflet 1.9.4 (map tiles © OpenStreetMap / CARTO) and Chart.js 4.4.1.

**One design, two experiences.** Every page shares one look (`assets/css/app.css`, `components.js`, the header with the notifications bell and the footer in `ui.js`). Investor pages keep the investor flow and wording; resident, landing, sign-up and admin pages keep theirs. Pages both roles open (`idea`, `projects`, `account`) load a small module per role: `<page>.js` picks `<page>-investor.js` or `<page>-resident.js`. Where the two sides word the same thing differently, the investor pages use an `…Inv.` copy of the key in `strings.js` (e.g. `projInv.myIdeas`).

**Language and voice.** Arabic is the default (English is one tap away and remembered). All text lives in `assets/js/strings.js`, so the wording can change without touching the pages. The investor side speaks casual Saudi dialect, gender-neutral where possible, and stays calm and plain on payment and contract screens.

**Demo data for the investor side.** In Postman, the folder **16 - Investor demo dataset** (in both collections) fills an empty database with a full investor experience: 11 neighborhoods in Riyadh, Jeddah and Al Khobar, 176 residents, 14 ideas with business models, AI checks and competitors, real resident votes (7 ideas ready to unlock, the rest still collecting), feasibility reports and three investor accounts (`investor.demo@naseej.test` verified with preferences and a draft contract, `investor.two@naseej.test` verified, `investor.new@naseej.test` brand new). Set `adminEmail` / `adminPassword` in the collection variables, then *Run folder*. Every account's password is `demoPassword` (default `Demo12345`). Running it again only adds what's missing. Unlock prices are quoted by Claude, so set `CLAUDE_API_KEY` to try the payment option.

**Investor flow.** Sign up (no CR) → onboarding questions → For you. The CR is asked for only when the investor first unlocks an opportunity (or from the account page); until an admin verifies it, browsing is open and unlocking is not. The menu has For you (with the map), Ask AI and My projects; the account opens from the avatar menu.

### Website endpoints

| Method | Path | Role |
|---|---|---|
| GET | `/public/stats`, `/public/ideas-nearby`, `/public/nearest-neighborhood` | public |
| PUT | `/auth/location` | logged in |
| GET | `/discover/map`, `/discover/idea/{id}`, `/discover/my-ideas` | resident, investor, admin |
| POST | `/discover/propose`, `/discover/retry-ai/{id}` | resident, investor |
| POST | `/discover/vote/{id}` | resident |
| GET | `/explore/cities`, `/explore/neighborhoods/{city}`, `/explore/opportunities/{neighborhoodId}` | investor |
| GET / POST | `/unlock/quote/{ideaId}`, `/unlock/pay/{ideaId}`, `/unlock/confirm-payment/{ideaId}`, `/unlock/partnership/{ideaId}`, `/unlock/adopt/{ideaId}` | investor (verified to unlock) |
| GET / POST | `/projects/mine`, `/projects/stages`, `/projects/stage/{contractId}` | resident, investor |
| GET / PUT | `/admin/stats`, `/admin/investors`, `/admin/investors/verify/{id}`, `/admin/investors/reject/{id}` | admin |

### Logic endpoints added

| Method | Path | Role | What it does |
|---|---|---|---|
| DELETE | `/discover/vote/{id}` | resident | Take a vote back while the idea is still collecting votes |
| GET | `/discover/my-votes` | resident | Ideas the resident voted for, to follow them until they open |
| PUT | `/discover/idea/{id}` | founder | Edit title/description of a published idea (title locked once someone voted) |
| DELETE | `/discover/idea/{id}` | founder | Withdraw a published idea or delete a rejected one |
| PUT | `/discover/idea/{id}/business-model/chat` | founder | Change the business model in your own words (Claude rewrites it) |
| PUT | `/auth/me` | logged in | Edit profile; a new CR number sends an investor back to admin review. Investors also send `investmentBudget` (a preset or `CUSTOM` with `budgetMin` / `budgetMax`), `investmentFields`, `investmentFieldsOther` (own words; the AI matches it to idea categories) and `investmentAreas` (neighborhood ids). An empty string clears a field |
| POST | `/explore/ask` | investor | `{ "question": "…" }` → the filters the AI read (city, neighborhood, category, capital, ready to open) and the matching opportunities |
| POST | `/projects/{contractId}/sign-otp` | investor | Email the 6-digit signing code to the investor |
| POST | `/projects/{contractId}/sign` | investor | Sign: typed full name + drawn signature + code; the contract becomes active right away (the idea owner doesn't sign) |
| GET | `/projects/{contractId}/contract` | investor, founder | Print-ready contract page (Arabic); "Save as PDF" in the browser |
| GET | `/payments/mine` | investor | Own payments and invoices |
| POST | `/subscriptions/subscribe` | investor | Start a BASIC (299) or PRO (999) monthly subscription through Moyasar |
| GET | `/subscriptions/mine` | investor | Own subscriptions (checks pending payments with Moyasar) |
| PUT | `/subscriptions/cancel` | investor | Cancel; a paid month keeps working until its end date |
| GET | `/explore/search` | investor | Search by words, city, category and `maxCapital` (from the business model) |
| GET | `/explore/rising` | investor | Requests that gained the most votes in the last `days` (default 7) |
| PUT | `/admin/users/{id}/disable` | admin | Disable an account (`?disabled=false` re-enables it) |
| PUT | `/admin/ideas/{id}/hide` | admin | Hide an idea from the map and lists (`?hidden=false` restores it) |
| POST | `/admin/payments/{id}/refund` | admin | Refund through Moyasar's Refund API and undo what the payment unlocked |
| POST | `/admin/contracts/expire-overdue` | admin | Run the deadline check now: 14 days to sign, 180 days to open (it also runs every night) |
| GET / PUT | `/account/profile` | logged in | Own profile (name, phone, language; investors also company, CR, budget range and fields) |
| GET / POST / PUT / DELETE | `/account/addresses`, `/account/addresses/{id}`, `/account/addresses/{id}/primary`, `/account/addresses/preview` | logged in | Saved home addresses (residents vote from them; up to two, one change every 30 days). `preview` shows what a change would do to the votes before saving |
| GET / PUT | `/notifications`, `/notifications/unread-count`, `/notifications/read-all`, `/notifications/{id}/read` | logged in | The bell in the header: idea adopted, vote target reached, project stage |
| PUT / DELETE | `/discover/draft/{id}` | founder | Edit or drop a draft before publishing |
| POST | `/discover/publish/{id}` | founder | Publish a draft that passed the AI check (the check must be under a day old) |
| GET | `/public/search-places` | public | Address search for the home pin and the propose map (OpenStreetMap) |
| GET / POST | `/explore/top`, `/explore/match`, `/explore/ai-search` | investor | Strongest opportunities, matches by city / neighborhood / budget range, and the AI search with a reason per result |
| GET / POST | `/unlock/checkout/{ideaId}`, `/unlock/owner-terms/{ideaId}`, `/unlock/owner-checkout/{ideaId}`, `/unlock/owner-confirm/{ideaId}` | investor | Moyasar payment form checkout, and the paid launch of the investor's own idea (service fee `OWNER_LAUNCH_FEE_SAR`). The investor pages use `/unlock/pay` and `/unlock/adopt` |
| GET | `/unlock/agreement/{contractId}` | investor, founder, admin | The agreement behind a project |
| GET / POST / PUT / DELETE | `/admin/users`, `/admin/users/{id}`, `/admin/users/{id}/password`, `/admin/users/{id}/suspend`, `/admin/users/{id}/reactivate` | admin | Member management |

**Contract flow after adoption:** unlocking or adopting creates a `DRAFT` contract → the investor signs → `ACTIVE` right away, and the 180-day opening deadline starts. The idea owner doesn't sign and gets no discount; they're notified when the contract is active. The investor must sign within 14 days of the purchase. Once, in the last 30 days before the opening deadline, an investor who posted progress in the last 30 days can extend it by 60 days (`POST /projects/{contractId}/extend`); progress can also be posted on the current stage with a note. An investor who adopts their own idea skips the benefits step and signs once. A contract that isn't signed or doesn't open by its deadline is terminated and the idea goes back to the other investors (`COMPLETED`) or back to voting (`PUBLISHED`).

The raw CRUD and AI endpoints (`/ideas/**`, `/payments/**`, `/contracts/**`, …) take user ids in the request body, so they are now **admin only**; the website uses the endpoints above, which read the user from the token.

## 👥 Team

<table>
  <tr>
    <td align="center" width="33%">
      <a href="https://github.com/AHMEDALHAMAMI"><img src="https://github.com/AHMEDALHAMAMI.png" width="100" alt="Member"><br><b>Ahmad Alhamami</b></a><br>
      <sub><img src="https://cdn.simpleicons.org/anthropic" width="16"> AI feature</sub><br><br>
      User, Idea, FoundingMember, Subscription, Neighborhood<br><br>
      <b>0</b> extra endpoints
    </td>
    <td align="center" width="33%">
      <a href="https://github.com/WaleedAlaql"><img src="https://github.com/WaleedAlaql.png" width="100" alt="Member"><br><b>Waleed Alaql</b></a><br>
      <sub><img src="https://cdn.simpleicons.org/anthropic" width="16"> AI feature</sub><br><br>
      Contract, Payment, PartnershipAgreement, ContractSignature, ProjectUpdate<br><br>
      <b>0</b> extra endpoints
    </td>
    <td align="center" width="33%">
      <a href="https://github.com/AmiraAldajani"><img src="https://github.com/AmiraAldajani.png" width="100" alt="Member"><br><b>Amira Aldajani</b></a><br>
      <sub><img src="https://cdn.simpleicons.org/anthropic" width="16"> AI feature</sub><br><br>
      IdeaCheck, BusinessModel, OpportunityAccess, FeasibilityReport, NearbyPlace<br><br>
      <b>0</b> extra endpoints
    </td>
  </tr>
</table>

## 🧩 Endpoints

<img src="https://cdn.simpleicons.org/anthropic" width="16">

<details open>
<summary>&nbsp;<b>Authentication</b> (7)</summary>

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/v1/api/auth/register` | Sign up with email, password, name, neighborhood, mobile and membership type |
| `POST` | `/v1/api/auth/verify-otp` | Verify the 6-digit email code |
| `POST` | `/v1/api/auth/login` | Log in and get access and refresh tokens |
| `POST` | `/v1/api/auth/refresh` | Get a new access token from the refresh token |
| `POST` | `/v1/api/auth/logout` | Log out |
| `GET` | `/oauth2/authorization/google` | Start Google sign-in |
| `PUT` | `/v1/api/auth/select-role` | Choose resident or investor after Google sign-in |

</details>

<!-- Add one <details> block per team member with their endpoints, in the same format as Authentication above -->

## 📬 Contact

<table>
  <tr>
    <td align="center" width="33%">
      <a href="https://www.linkedin.com/in/ahmedalhamami"><img src="https://github.com/AHMEDALHAMAMI.png" width="100" alt="Ahmad Alhamami"><br><b>Ahmad Alhamami</b></a><br><br>
      <a href="https://www.linkedin.com/in/ahmedalhamami"><img src="https://img.shields.io/badge/LinkedIn-0A66C2?style=flat&logo=linkedin&logoColor=white" alt="LinkedIn"></a>
    </td>
    <td align="center" width="33%">
      <a href="https://www.linkedin.com/in/waleed-alaql"><img src="https://github.com/WaleedAlaql.png" width="100" alt="Waleed Alaql"><br><b>Waleed Alaql</b></a><br><br>
      <a href="https://www.linkedin.com/in/waleed-alaql"><img src="https://img.shields.io/badge/LinkedIn-0A66C2?style=flat&logo=linkedin&logoColor=white" alt="LinkedIn"></a>
    </td>
    <td align="center" width="33%">
      <a href="https://www.linkedin.com/in/amira-aldajani-2b0296222/"><img src="https://github.com/AmiraAldajani.png" width="100" alt="Amira Aldajani"><br><b>Amira Aldajani</b></a><br><br>
      <a href="https://www.linkedin.com/in/amira-aldajani-2b0296222/"><img src="https://img.shields.io/badge/LinkedIn-0A66C2?style=flat&logo=linkedin&logoColor=white" alt="LinkedIn"></a>
    </td>
  </tr>
</table>
