// The idea page. Investors get the investor experience (idea-investor.js); residents and admins get
// idea-resident.js. Both share the same page shell, header and styles.
import { session } from '../api.js';

const role = session.user()?.role;
await import(role === 'INVESTOR' ? './idea-investor.js' : './idea-resident.js');
