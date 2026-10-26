// My account. Investors get the investor experience (account-investor.js); residents and admins get
// account-resident.js. Both share the same page shell, header and styles.
import { session } from '../api.js';

const role = session.user()?.role;
await import(role === 'INVESTOR' ? './account-investor.js' : './account-resident.js');
