# توزيع الـ Endpoints على الأعضاء (متساوٍ تقريبًا)

كل عضو يحتفظ بكلاسات الـ CRUD الخاصة به من الجدول (25 endpoint لكل عضو)، وتُوزَّع الـ 122 endpoint الإضافية بالتساوي، مع ربط كل واحد بالموضوع الأقرب له.

| العضو | الإجمالي | CRUD | إضافية |
|---|---|---|---|
| Ahmad Alhamami | 66 | 25 | 41 |
| Amira Aldajani | 66 | 25 | 41 |
| Waleed Alaql | 65 | 25 | 40 |
| **المجموع** | **197** | **75** | **122** |

## Ahmad Alhamami — العضو 1: الحسابات والمجتمع

### Account (8)

| Method | Endpoint | النوع |
|---|---|---|
| `GET` | `/account/profile` | إضافي |
| `PUT` | `/account/profile` | إضافي |
| `GET` | `/account/addresses` | إضافي |
| `POST` | `/account/addresses` | إضافي |
| `PUT` | `/account/addresses/{id}` | إضافي |
| `PUT` | `/account/addresses/{id}/primary` | إضافي |
| `DELETE` | `/account/addresses/{id}` | إضافي |
| `POST` | `/account/addresses/preview` | إضافي |

### Admin (13)

| Method | Endpoint | النوع |
|---|---|---|
| `GET` | `/admin/stats` | إضافي |
| `GET` | `/admin/users` | إضافي |
| `POST` | `/admin/users` | إضافي |
| `PUT` | `/admin/users/{id}` | إضافي |
| `PUT` | `/admin/users/{id}/suspend` | إضافي |
| `PUT` | `/admin/users/{id}/reactivate` | إضافي |
| `PUT` | `/admin/users/{id}/password` | إضافي |
| `DELETE` | `/admin/users/{id}` | إضافي |
| `GET` | `/admin/investors` | إضافي |
| `PUT` | `/admin/investors/verify/{id}` | إضافي |
| `PUT` | `/admin/investors/reject/{id}` | إضافي |
| `PUT` | `/admin/users/{id}/disable` | إضافي |
| `PUT` | `/admin/ideas/{id}/hide` | إضافي |

### Auth (11)

| Method | Endpoint | النوع |
|---|---|---|
| `POST` | `/auth/register` | إضافي |
| `POST` | `/auth/verify-otp` | إضافي |
| `POST` | `/auth/send-otp` | إضافي |
| `POST` | `/auth/login` | إضافي |
| `POST` | `/auth/reset-password` | إضافي |
| `POST` | `/auth/refresh` | إضافي |
| `PUT` | `/auth/select-role` | إضافي |
| `GET` | `/auth/me` | إضافي |
| `PUT` | `/auth/me` | إضافي |
| `PUT` | `/auth/location` | إضافي |
| `POST` | `/auth/logout` | إضافي |

### Explore (2)

| Method | Endpoint | النوع |
|---|---|---|
| `GET` | `/explore/cities` | إضافي |
| `GET` | `/explore/neighborhoods/{city}` | إضافي |

### FoundingMember (5) — منها 5 CRUD

| Method | Endpoint | النوع |
|---|---|---|
| `GET` | `/founding-members/get-all` | CRUD |
| `POST` | `/founding-members/create` | CRUD |
| `PUT` | `/founding-members/update/{id}` | CRUD |
| `DELETE` | `/founding-members/delete/{id}` | CRUD |
| `GET` | `/founding-members/get/{id}` | CRUD |

### Idea (5) — منها 5 CRUD

| Method | Endpoint | النوع |
|---|---|---|
| `GET` | `/ideas/get-all` | CRUD |
| `POST` | `/ideas/create` | CRUD |
| `PUT` | `/ideas/update/{id}` | CRUD |
| `DELETE` | `/ideas/delete/{id}` | CRUD |
| `GET` | `/ideas/get/{id}` | CRUD |

### Neighborhood (7) — منها 5 CRUD

| Method | Endpoint | النوع |
|---|---|---|
| `GET` | `/neighborhoods/get-all` | CRUD |
| `POST` | `/neighborhoods/create` | CRUD |
| `PUT` | `/neighborhoods/update/{id}` | CRUD |
| `DELETE` | `/neighborhoods/delete/{id}` | CRUD |
| `GET` | `/neighborhoods/get/{id}` | CRUD |
| `GET` | `/neighborhoods/get-by-city/{city}` | إضافي |
| `GET` | `/neighborhoods/search/{keyword}` | إضافي |

### Public (5)

| Method | Endpoint | النوع |
|---|---|---|
| `GET` | `/public/stats` | إضافي |
| `GET` | `/public/ideas-nearby` | إضافي |
| `GET` | `/public/nearest-neighborhood` | إضافي |
| `GET` | `/public/search-places` | إضافي |
| `GET` | `/public/map-config` | إضافي |

### Subscription (5) — منها 5 CRUD

| Method | Endpoint | النوع |
|---|---|---|
| `GET` | `/subscriptions/get-all` | CRUD |
| `POST` | `/subscriptions/create` | CRUD |
| `PUT` | `/subscriptions/update/{id}` | CRUD |
| `DELETE` | `/subscriptions/delete/{id}` | CRUD |
| `GET` | `/subscriptions/get/{id}` | CRUD |

### User (5) — منها 5 CRUD

| Method | Endpoint | النوع |
|---|---|---|
| `GET` | `/users/get-all` | CRUD |
| `POST` | `/users/create` | CRUD |
| `PUT` | `/users/update/{id}` | CRUD |
| `DELETE` | `/users/delete/{id}` | CRUD |
| `GET` | `/users/get/{id}` | CRUD |

## Amira Aldajani — العضو 2: الذكاء الاصطناعي ورحلة المستثمر

### BusinessModel (9) — منها 5 CRUD

| Method | Endpoint | النوع |
|---|---|---|
| `GET` | `/business-models/get-all` | CRUD |
| `POST` | `/business-models/create` | CRUD |
| `PUT` | `/business-models/update/{id}` | CRUD |
| `PUT` | `/business-models/revise-by-chat/{id}` | إضافي |
| `DELETE` | `/business-models/delete/{id}` | CRUD |
| `GET` | `/business-models/get/{id}` | CRUD |
| `POST` | `/business-models/generate/{ideaId}` | إضافي |
| `GET` | `/business-models/get-by-idea/{ideaId}` | إضافي |
| `GET` | `/business-models/search-by-capital` | إضافي |

### Discover (14)

| Method | Endpoint | النوع |
|---|---|---|
| `GET` | `/discover/map` | إضافي |
| `GET` | `/discover/idea/{id}` | إضافي |
| `GET` | `/discover/my-ideas` | إضافي |
| `POST` | `/discover/propose` | إضافي |
| `PUT` | `/discover/draft/{id}` | إضافي |
| `POST` | `/discover/publish/{id}` | إضافي |
| `DELETE` | `/discover/draft/{id}` | إضافي |
| `POST` | `/discover/retry-ai/{id}` | إضافي |
| `POST` | `/discover/vote/{id}` | إضافي |
| `DELETE` | `/discover/vote/{id}` | إضافي |
| `GET` | `/discover/my-votes` | إضافي |
| `PUT` | `/discover/idea/{id}` | إضافي |
| `DELETE` | `/discover/idea/{id}` | إضافي |
| `PUT` | `/discover/idea/{id}/business-model/chat` | إضافي |

### Explore (7)

| Method | Endpoint | النوع |
|---|---|---|
| `POST` | `/explore/ask` | إضافي |
| `GET` | `/explore/opportunities/{neighborhoodId}` | إضافي |
| `GET` | `/explore/search` | إضافي |
| `GET` | `/explore/rising` | إضافي |
| `GET` | `/explore/top` | إضافي |
| `GET` | `/explore/match` | إضافي |
| `POST` | `/explore/ai-search` | إضافي |

### FeasibilityReport (8) — منها 5 CRUD

| Method | Endpoint | النوع |
|---|---|---|
| `GET` | `/feasibility-reports/get-all` | CRUD |
| `POST` | `/feasibility-reports/create` | CRUD |
| `PUT` | `/feasibility-reports/update/{id}` | CRUD |
| `DELETE` | `/feasibility-reports/delete/{id}` | CRUD |
| `GET` | `/feasibility-reports/get/{id}` | CRUD |
| `POST` | `/feasibility-reports/generate/{ideaId}` | إضافي |
| `GET` | `/feasibility-reports/preview/{ideaId}` | إضافي |
| `GET` | `/feasibility-reports/get-full/{ideaId}` | إضافي |

### IdeaCheck (8) — منها 5 CRUD

| Method | Endpoint | النوع |
|---|---|---|
| `GET` | `/idea-checks/get-all` | CRUD |
| `POST` | `/idea-checks/create` | CRUD |
| `PUT` | `/idea-checks/update/{id}` | CRUD |
| `DELETE` | `/idea-checks/delete/{id}` | CRUD |
| `GET` | `/idea-checks/get/{id}` | CRUD |
| `POST` | `/idea-checks/run/{ideaId}` | إضافي |
| `GET` | `/idea-checks/get-by-idea/{ideaId}` | إضافي |
| `GET` | `/idea-checks/get-by-decision/{decision}` | إضافي |

### NearbyPlace (8) — منها 5 CRUD

| Method | Endpoint | النوع |
|---|---|---|
| `GET` | `/nearby-places/get-all` | CRUD |
| `POST` | `/nearby-places/create` | CRUD |
| `PUT` | `/nearby-places/update/{id}` | CRUD |
| `DELETE` | `/nearby-places/delete/{id}` | CRUD |
| `GET` | `/nearby-places/get/{id}` | CRUD |
| `GET` | `/nearby-places/get-by-idea/{ideaId}` | إضافي |
| `GET` | `/nearby-places/count-by-idea/{ideaId}` | إضافي |
| `POST` | `/nearby-places/refresh/{ideaId}` | إضافي |

### OpportunityAccess (9) — منها 5 CRUD

| Method | Endpoint | النوع |
|---|---|---|
| `GET` | `/opportunity-access/get-all` | CRUD |
| `POST` | `/opportunity-access/create` | CRUD |
| `PUT` | `/opportunity-access/update/{id}` | CRUD |
| `DELETE` | `/opportunity-access/delete/{id}` | CRUD |
| `GET` | `/opportunity-access/get/{id}` | CRUD |
| `POST` | `/opportunity-access/unlock` | إضافي |
| `GET` | `/opportunity-access/check` | إضافي |
| `GET` | `/opportunity-access/get-by-investor/{investorId}` | إضافي |
| `GET` | `/opportunity-access/count-by-idea/{ideaId}` | إضافي |

### Unlock (3)

| Method | Endpoint | النوع |
|---|---|---|
| `GET` | `/unlock/quote/{ideaId}` | إضافي |
| `POST` | `/unlock/adopt/{ideaId}` | إضافي |
| `GET` | `/unlock/owner-terms/{ideaId}` | إضافي |

## Waleed Alaql — العضو 3: الدفع والعقود

### Admin (2)

| Method | Endpoint | النوع |
|---|---|---|
| `POST` | `/admin/payments/{id}/refund` | إضافي |
| `POST` | `/admin/contracts/expire-overdue` | إضافي |

### Contract (9) — منها 5 CRUD

| Method | Endpoint | النوع |
|---|---|---|
| `GET` | `/contracts/get-all` | CRUD |
| `POST` | `/contracts/create` | CRUD |
| `PUT` | `/contracts/update/{id}` | CRUD |
| `DELETE` | `/contracts/delete/{id}` | CRUD |
| `GET` | `/contracts/get/{id}` | CRUD |
| `POST` | `/contracts/draft` | إضافي |
| `POST` | `/contracts/sign/{id}` | إضافي |
| `POST` | `/contracts/terminate/{id}` | إضافي |
| `POST` | `/contracts/add-update/{id}` | إضافي |

### ContractSignature (7) — منها 5 CRUD

| Method | Endpoint | النوع |
|---|---|---|
| `GET` | `/contract-signatures/get-all` | CRUD |
| `POST` | `/contract-signatures/create` | CRUD |
| `PUT` | `/contract-signatures/update/{id}` | CRUD |
| `DELETE` | `/contract-signatures/delete/{id}` | CRUD |
| `GET` | `/contract-signatures/get/{id}` | CRUD |
| `POST` | `/contract-signatures/sign/{contractId}` | إضافي |
| `GET` | `/contract-signatures/check/contract/{contractId}` | إضافي |

### Notification (4)

| Method | Endpoint | النوع |
|---|---|---|
| `GET` | `/notifications` | إضافي |
| `GET` | `/notifications/unread-count` | إضافي |
| `PUT` | `/notifications/read-all` | إضافي |
| `PUT` | `/notifications/{id}/read` | إضافي |

### PartnershipAgreement (7) — منها 5 CRUD

| Method | Endpoint | النوع |
|---|---|---|
| `GET` | `/partnership-agreements/get-all` | CRUD |
| `POST` | `/partnership-agreements/create` | CRUD |
| `PUT` | `/partnership-agreements/update/{id}` | CRUD |
| `DELETE` | `/partnership-agreements/delete/{id}` | CRUD |
| `GET` | `/partnership-agreements/get/{id}` | CRUD |
| `POST` | `/partnership-agreements/accept-terms/{id}` | إضافي |
| `GET` | `/partnership-agreements/check/access/{accessId}` | إضافي |

### Payment (13) — منها 5 CRUD

| Method | Endpoint | النوع |
|---|---|---|
| `GET` | `/payments/get-all` | CRUD |
| `POST` | `/payments/create` | CRUD |
| `PUT` | `/payments/update/{id}` | CRUD |
| `DELETE` | `/payments/delete/{id}` | CRUD |
| `GET` | `/payments/get/{id}` | CRUD |
| `GET` | `/payments/get-status/{id}` | إضافي |
| `POST` | `/payments/confirm/{id}` | إضافي |
| `POST` | `/payments/refund/{id}` | إضافي |
| `POST` | `/payments/webhook/moyasar` | إضافي |
| `POST` | `/payments/initiate-moyasar` | إضافي |
| `POST` | `/payments/sync-moyasar/{id}` | إضافي |
| `GET` | `/payments/moyasar-callback` | إضافي |
| `GET` | `/payments/mine` | إضافي |

### Project (8)

| Method | Endpoint | النوع |
|---|---|---|
| `GET` | `/projects/mine` | إضافي |
| `GET` | `/projects/stages` | إضافي |
| `POST` | `/projects/stage/{contractId}` | إضافي |
| `PUT` | `/projects/{contractId}/benefits` | إضافي |
| `POST` | `/projects/{contractId}/sign-otp` | إضافي |
| `POST` | `/projects/{contractId}/sign` | إضافي |
| `POST` | `/projects/{contractId}/extend` | إضافي |
| `GET` | `/projects/{contractId}/contract` | إضافي |

### ProjectUpdate (5) — منها 5 CRUD

| Method | Endpoint | النوع |
|---|---|---|
| `GET` | `/project-updates/get-all` | CRUD |
| `POST` | `/project-updates/create` | CRUD |
| `PUT` | `/project-updates/update/{id}` | CRUD |
| `DELETE` | `/project-updates/delete/{id}` | CRUD |
| `GET` | `/project-updates/get/{id}` | CRUD |

### Subscription (3)

| Method | Endpoint | النوع |
|---|---|---|
| `POST` | `/subscriptions/subscribe` | إضافي |
| `GET` | `/subscriptions/mine` | إضافي |
| `PUT` | `/subscriptions/cancel` | إضافي |

### Unlock (7)

| Method | Endpoint | النوع |
|---|---|---|
| `POST` | `/unlock/pay/{ideaId}` | إضافي |
| `POST` | `/unlock/checkout/{ideaId}` | إضافي |
| `POST` | `/unlock/confirm-payment/{ideaId}` | إضافي |
| `POST` | `/unlock/partnership/{ideaId}` | إضافي |
| `POST` | `/unlock/owner-checkout/{ideaId}` | إضافي |
| `POST` | `/unlock/owner-confirm/{ideaId}` | إضافي |
| `GET` | `/unlock/agreement/{contractId}` | إضافي |
