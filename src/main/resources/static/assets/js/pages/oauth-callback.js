// Google sign-in lands here with our tokens in the URL fragment (#...), which never reaches the server.
import { api, session } from '../api.js';
import { $, homeFor, initPage, t } from '../ui.js';
import { langButton } from '../i18n.js';

$('#lang-slot').innerHTML = langButton();
initPage();

const data = new URLSearchParams(location.hash.slice(1));
history.replaceState(null, '', location.pathname); // remove the tokens from the address bar

const accessToken = data.get('accessToken');
const refreshToken = data.get('refreshToken');

if (!accessToken || !refreshToken) {
  location.replace('login.html?error=google');
} else {
  session.save({ accessToken, refreshToken, roleSelectionRequired: data.get('roleSelectionRequired') === 'true' });
  api.get('auth/me')
    .then((user) => {
      session.setUser(user);
      location.replace(user.role === 'PENDING' ? 'choose-role.html' : homeFor(user));
    })
    .catch(() => {
      $('#status').textContent = t('oauth.failed');
      setTimeout(() => location.replace('login.html?error=google'), 1500);
    });
}
