// My projects / my ideas. Investors get the investor experience (projects-investor.js); residents and admins get
// projects-resident.js. Both share the same page shell, header and styles.
import { session } from '../api.js';

const role = session.user()?.role;
await import(role === 'INVESTOR' ? './projects-investor.js' : './projects-resident.js');
