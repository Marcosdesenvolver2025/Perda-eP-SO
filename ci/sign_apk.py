#!/usr/bin/env python3
"""
Assina um APK com o APK Signature Scheme v2 (suficiente para Android 7+; o MusiBox exige Android 10+).

Uso:
  python3 ci/sign_apk.py entrada-unsigned.apk saida.apk keystore.p12 SENHA
  python3 ci/sign_apk.py --verify app.apk

O APK de entrada precisa estar alinhado (os APKs "-unsigned" gerados pelo Gradle já estão).
O conteúdo do APK não é alterado: o bloco de assinatura é inserido antes do diretório central do ZIP.
Requer: pip install cryptography
"""
import hashlib
import struct
import sys

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.serialization import pkcs12

V2_BLOCK_ID = 0x7109871A
RSA_PKCS1_SHA256 = 0x0103
MAGIC = b"APK Sig Block 42"
CHUNK = 1 << 20


def lp(b: bytes) -> bytes:
    return struct.pack("<I", len(b)) + b


def find_eocd(data: bytes) -> int:
    lowest = max(0, len(data) - 22 - 0xFFFF)
    for i in range(len(data) - 22, lowest - 1, -1):
        if data[i:i + 4] == b"PK\x05\x06":
            comment_len = struct.unpack("<H", data[i + 20:i + 22])[0]
            if i + 22 + comment_len == len(data):
                return i
    raise ValueError("Fim do diretório central do ZIP não encontrado")


def content_digest(sections) -> bytes:
    digests = []
    for sec in sections:
        for off in range(0, len(sec), CHUNK):
            c = sec[off:off + CHUNK]
            digests.append(hashlib.sha256(b"\xa5" + struct.pack("<I", len(c)) + c).digest())
    return hashlib.sha256(b"\x5a" + struct.pack("<I", len(digests)) + b"".join(digests)).digest()


def zip_parts(data: bytes):
    eocd = find_eocd(data)
    cd_size, cd_offset = struct.unpack("<II", data[eocd + 12:eocd + 20])
    if cd_offset + cd_size != eocd:
        raise ValueError("ZIP com dados extras entre o diretório central e o EOCD")
    return eocd, cd_offset, cd_size


def sign(inp: str, out: str, keystore: str, password: str) -> None:
    data = open(inp, "rb").read()
    eocd, cd_offset, _ = zip_parts(data)
    if data[cd_offset - 16:cd_offset] == MAGIC:
        raise ValueError("O APK já tem bloco de assinatura; use o APK -unsigned")

    key, cert, _ = pkcs12.load_key_and_certificates(open(keystore, "rb").read(), password.encode())
    cert_der = cert.public_bytes(serialization.Encoding.DER)
    pub_der = key.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
    )

    contents = data[:cd_offset]
    central = data[cd_offset:eocd]
    end = data[eocd:]
    digest = content_digest([contents, central, end])

    signed_data = (
        lp(lp(struct.pack("<I", RSA_PKCS1_SHA256) + lp(digest)))
        + lp(lp(cert_der))
        + lp(b"")
    )
    signature = key.sign(signed_data, padding.PKCS1v15(), hashes.SHA256())
    signer = (
        lp(signed_data)
        + lp(lp(struct.pack("<I", RSA_PKCS1_SHA256) + lp(signature)))
        + lp(pub_der)
    )
    v2_value = lp(lp(signer))

    pairs = struct.pack("<Q", 4 + len(v2_value)) + struct.pack("<I", V2_BLOCK_ID) + v2_value
    block_size = len(pairs) + 8 + 16
    block = struct.pack("<Q", block_size) + pairs + struct.pack("<Q", block_size) + MAGIC

    new_end = bytearray(end)
    struct.pack_into("<I", new_end, 16, cd_offset + len(block))
    with open(out, "wb") as f:
        f.write(contents)
        f.write(block)
        f.write(central)
        f.write(bytes(new_end))


def read_lp(buf: bytes, pos: int):
    n = struct.unpack("<I", buf[pos:pos + 4])[0]
    return buf[pos + 4:pos + 4 + n], pos + 4 + n


def seq(buf: bytes):
    items, pos = [], 0
    while pos < len(buf):
        item, pos = read_lp(buf, pos)
        items.append(item)
    return items


def verify(path: str) -> str:
    data = open(path, "rb").read()
    eocd, cd_offset, _ = zip_parts(data)
    if data[cd_offset - 16:cd_offset] != MAGIC:
        raise ValueError("APK sem bloco de assinatura v2")
    size2 = struct.unpack("<Q", data[cd_offset - 24:cd_offset - 16])[0]
    block_start = cd_offset - size2 - 8
    size1 = struct.unpack("<Q", data[block_start:block_start + 8])[0]
    if size1 != size2:
        raise ValueError("Tamanhos do bloco de assinatura não conferem")
    pairs = data[block_start + 8:cd_offset - 24]
    v2 = None
    pos = 0
    while pos < len(pairs):
        n = struct.unpack("<Q", pairs[pos:pos + 8])[0]
        pid = struct.unpack("<I", pairs[pos + 8:pos + 12])[0]
        if pid == V2_BLOCK_ID:
            v2 = pairs[pos + 12:pos + 8 + n]
        pos += 8 + n
    if v2 is None:
        raise ValueError("Bloco v2 não encontrado")
    signers, _ = read_lp(v2, 0)
    signer = seq(signers)[0]
    signed_data, p = read_lp(signer, 0)
    signatures, p = read_lp(signer, p)
    pub, _ = read_lp(signer, p)
    alg_sig = seq(signatures)[0]
    alg = struct.unpack("<I", alg_sig[:4])[0]
    sig, _ = read_lp(alg_sig, 4)
    if alg != RSA_PKCS1_SHA256:
        raise ValueError("Algoritmo inesperado")
    public_key = serialization.load_der_public_key(pub)
    public_key.verify(sig, signed_data, padding.PKCS1v15(), hashes.SHA256())

    digests_seq, q = read_lp(signed_data, 0)
    certs_seq, _ = read_lp(signed_data, q)
    d = seq(digests_seq)[0]
    expected, _ = read_lp(d, 4)
    end = bytearray(data[eocd:])
    struct.pack_into("<I", end, 16, block_start)
    actual = content_digest([data[:block_start], data[cd_offset:eocd], bytes(end)])
    if actual != expected:
        raise ValueError("Resumo do conteúdo não confere (APK alterado)")
    cert = x509.load_der_x509_certificate(seq(certs_seq)[0])
    sha1 = cert.fingerprint(hashes.SHA1()).hex().upper()
    return ":".join(sha1[i:i + 2] for i in range(0, len(sha1), 2))


if __name__ == "__main__":
    if sys.argv[1] == "--verify":
        print("OK, assinatura v2 válida. SHA-1 do certificado:", verify(sys.argv[2]))
    else:
        sign(sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4])
        print("Assinado:", sys.argv[2], "SHA-1:", verify(sys.argv[2]))
