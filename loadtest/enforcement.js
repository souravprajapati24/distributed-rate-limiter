import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const tenants = [
    { name: 'FREE (FIXED_WINDOW)',                key: __ENV.FREE_KEY },
    { name: 'STARTER (SLIDING_WINDOW)',            key: __ENV.STARTER_KEY },
    { name: 'GROWTH (TOKEN_BUCKET)',               key: __ENV.GROWTH_KEY },
    { name: 'INTERNAL_DOWNSTREAM (LEAKY_BUCKET)',  key: __ENV.LEAKY_KEY },
];

export const options = {
    thresholds: {
        http_req_duration: ['p(99)<50'],
        checks: ['rate>0.99'],
        http_req_failed: ['rate<0.01'],
    },
};

export default function () {
    const tenant = tenants[Math.floor(Math.random() * tenants.length)];

    const res = http.get(
        `${BASE_URL}/api/v1/test`,
        {
            headers: {
                'X-Api-Key': tenant.key,
            },
            responseCallback: http.expectedStatuses(200, 429),
        }
    );

    check(res, {
        'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
        'has X-RateLimit-Algorithm header': (r) => r.headers['X-Ratelimit-Algorithm'] !== undefined,
    });

    //sleep(0.1);
}