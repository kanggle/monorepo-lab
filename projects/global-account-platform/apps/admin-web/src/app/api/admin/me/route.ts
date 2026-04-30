import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';

export async function GET() {
  const jar = await cookies();
  const access = jar.get('accessToken')?.value;
  if (!access) {
    return NextResponse.json({ code: 'TOKEN_INVALID', message: 'Access token missing' }, { status: 401 });
  }

  try {
    const parts = access.split('.');
    if (parts.length !== 3) {
      return NextResponse.json({ code: 'TOKEN_INVALID', message: 'Malformed token' }, { status: 401 });
    }
    const payload = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf-8'));
    const now = Math.floor(Date.now() / 1000);
    if (payload.exp && payload.exp < now) {
      return NextResponse.json({ code: 'TOKEN_EXPIRED', message: 'Access token expired' }, { status: 401 });
    }
    return NextResponse.json({
      operatorId: payload.sub ?? '',
      email: payload.email ?? payload.sub ?? '',
      roles: payload.roles ?? ['SUPER_ADMIN'],
    });
  } catch {
    return NextResponse.json({ code: 'TOKEN_INVALID', message: 'Token decode failed' }, { status: 401 });
  }
}
