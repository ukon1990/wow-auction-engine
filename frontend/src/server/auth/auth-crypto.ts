import { createCipheriv, createDecipheriv, createHash, randomBytes } from 'node:crypto';

const encryptionVersion = 'v1';

export function createPkcePair(): { verifier: string; challenge: string } {
  const verifier = base64Url(randomBytes(32));
  const challenge = base64Url(createHash('sha256').update(verifier).digest());
  return { verifier, challenge };
}

export function createOpaqueState(): string {
  return base64Url(randomBytes(24));
}

export function encryptPayload(payload: unknown, secret: string): string {
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', keyFromSecret(secret), iv);
  const plaintext = Buffer.from(JSON.stringify(payload), 'utf8');
  const ciphertext = Buffer.concat([cipher.update(plaintext), cipher.final()]);
  const tag = cipher.getAuthTag();
  return [encryptionVersion, base64Url(iv), base64Url(tag), base64Url(ciphertext)].join('.');
}

export function decryptPayload<T>(value: string, secret: string): T | null {
  const [version, ivValue, tagValue, ciphertextValue] = value.split('.');
  if (version !== encryptionVersion || !ivValue || !tagValue || !ciphertextValue) {
    return null;
  }

  try {
    const decipher = createDecipheriv('aes-256-gcm', keyFromSecret(secret), fromBase64Url(ivValue));
    decipher.setAuthTag(fromBase64Url(tagValue));
    const plaintext = Buffer.concat([
      decipher.update(fromBase64Url(ciphertextValue)),
      decipher.final(),
    ]);
    return JSON.parse(plaintext.toString('utf8')) as T;
  } catch {
    return null;
  }
}

function keyFromSecret(secret: string): Buffer {
  return createHash('sha256').update(secret).digest();
}

function base64Url(value: Buffer): string {
  return value.toString('base64url');
}

function fromBase64Url(value: string): Buffer {
  return Buffer.from(value, 'base64url');
}
