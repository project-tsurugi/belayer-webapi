* verify the access token of each request with the authentication server (harinoki). The token was only decoded, without checking its signature or its expiration, so a token that harinoki did not issue, or one that had expired, was accepted.
    * a verified token is reused until the earlier of its expiration and `TSURUGI_AUTH_VERIFY_CACHE_MAX_MIN` (default `30` minutes, `0` verifies on every request).
    * while harinoki is not available, a token not verified before is rejected with 401.
    * with the `authmock` profile, which has no authentication server, only the kind (access token) and the expiration are checked.
