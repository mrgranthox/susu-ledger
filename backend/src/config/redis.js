require('dotenv').config();

let client = null;
let isRedisConnected = false;

const redisUrl = process.env.REDIS_URL || process.env.REDIS_TLS_URL;

if (redisUrl) {
  try {
    const redis = require('redis');
    client = redis.createClient({
      url: redisUrl,
      socket: {
        tls: redisUrl.startsWith('rediss://'),
        rejectUnauthorized: false
      }
    });

    client.on('error', (err) => {
      console.warn('[Redis] Connection warning (fallback to memory cache):', err.message);
      isRedisConnected = false;
    });

    client.on('connect', () => {
      console.log('⚡ [Redis] Connected successfully to Cloud Cache');
      isRedisConnected = true;
    });

    client.connect().catch((err) => {
      console.warn('[Redis] Connect failed, using in-memory store:', err.message);
    });
  } catch (err) {
    console.warn('[Redis] Init error, using in-memory store:', err.message);
  }
}

// In-Memory Fallback Cache with TTL support
const memoryStore = new Map();

const cacheService = {
  async set(key, value, ttlSeconds = null) {
    if (isRedisConnected && client) {
      try {
        if (ttlSeconds) {
          await client.setEx(key, ttlSeconds, JSON.stringify(value));
        } else {
          await client.set(key, JSON.stringify(value));
        }
        return true;
      } catch (err) {
        console.warn('[Redis set error]', err.message);
      }
    }
    // Memory store fallback
    const expiry = ttlSeconds ? Date.now() + (ttlSeconds * 1000) : null;
    memoryStore.set(key, { value, expiry });
    return true;
  },

  async get(key) {
    if (isRedisConnected && client) {
      try {
        const data = await client.get(key);
        return data ? JSON.parse(data) : null;
      } catch (err) {
        console.warn('[Redis get error]', err.message);
      }
    }
    const item = memoryStore.get(key);
    if (!item) return null;
    if (item.expiry && Date.now() > item.expiry) {
      memoryStore.delete(key);
      return null;
    }
    return item.value;
  },

  async del(key) {
    if (isRedisConnected && client) {
      try {
        await client.del(key);
      } catch (err) {
        console.warn('[Redis del error]', err.message);
      }
    }
    memoryStore.delete(key);
    return true;
  },

  isAvailable() {
    return isRedisConnected;
  },

  async ping() {
    if (isRedisConnected && client) {
      try {
        return await client.ping();
      } catch (err) {
        return 'FAILED';
      }
    }
    return 'MEMORY_FALLBACK';
  }
};

module.exports = cacheService;
