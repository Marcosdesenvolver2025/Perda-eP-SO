import { test, before, after, beforeEach } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, deleteDoc, collection, getDocs } from 'firebase/firestore';

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-musibox',
    firestore: { rules: readFileSync('firestore.rules', 'utf8') },
  });
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'users/userA'), { email: 'a@exemplo.com' });
    await setDoc(doc(db, 'users/userA/playlists/p1'), { name: 'Treino', items: [] });
    await setDoc(doc(db, 'users/userA/favorites/f1'), { title: 'Musica' });
  });
});

test('usuário autenticado lê e grava os próprios dados', async () => {
  const db = env.authenticatedContext('userA').firestore();
  await assertSucceeds(getDoc(doc(db, 'users/userA')));
  await assertSucceeds(getDoc(doc(db, 'users/userA/playlists/p1')));
  await assertSucceeds(getDocs(collection(db, 'users/userA/favorites')));
  await assertSucceeds(setDoc(doc(db, 'users/userA/playlists/p2'), { name: 'Nova' }));
  await assertSucceeds(setDoc(doc(db, 'users/userA/settings/app'), { theme: 'DARK' }));
  await assertSucceeds(setDoc(doc(db, 'users/userA/history/h1'), { title: 'x' }));
  await assertSucceeds(setDoc(doc(db, 'users/userA/appData/lastPlayback'), { title: 'x' }));
  await assertSucceeds(deleteDoc(doc(db, 'users/userA/playlists/p1')));
});

test('outro usuário NÃO lê, grava nem apaga dados de outro UID', async () => {
  const db = env.authenticatedContext('userB').firestore();
  await assertFails(getDoc(doc(db, 'users/userA')));
  await assertFails(getDoc(doc(db, 'users/userA/playlists/p1')));
  await assertFails(getDocs(collection(db, 'users/userA/favorites')));
  await assertFails(setDoc(doc(db, 'users/userA/playlists/p1'), { name: 'invadida' }));
  await assertFails(setDoc(doc(db, 'users/userA'), { email: 'x' }));
  await assertFails(deleteDoc(doc(db, 'users/userA/favorites/f1')));
});

test('usuário sem login é bloqueado', async () => {
  const db = env.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(db, 'users/userA')));
  await assertFails(getDoc(doc(db, 'users/userA/playlists/p1')));
  await assertFails(setDoc(doc(db, 'users/userA/playlists/p9'), { name: 'x' }));
});

test('coleções fora da lista e caminhos fora de users são bloqueados', async () => {
  const db = env.authenticatedContext('userA').firestore();
  await assertFails(setDoc(doc(db, 'users/userA/segredos/x'), { a: 1 }));
  await assertFails(setDoc(doc(db, 'outros/x'), { a: 1 }));
  await assertFails(getDocs(collection(db, 'users')));
});
