"""Bounded public-page extraction; URLs and page text are never logged."""

import ipaddress
import socket
from urllib.parse import urlparse

import httpx
from bs4 import BeautifulSoup


class LinkExtractionError(Exception):
    pass


MAX_BYTES = 2 * 1024 * 1024
MAX_TEXT_CHARS = 100_000


def _is_public_host(url: str) -> bool:
    parsed = urlparse(url)
    if parsed.scheme not in {"http", "https"} or not parsed.hostname:
        return False
    try:
        addresses = socket.getaddrinfo(parsed.hostname, None, type=socket.SOCK_STREAM)
    except socket.gaierror:
        return False
    for address in addresses:
        ip = ipaddress.ip_address(address[4][0])
        if not ip.is_global:
            return False
    return bool(addresses)


async def extract_public_article(url: str) -> str:
    """Fetch a small HTML page without redirects into local/private networks."""
    if not _is_public_host(url):
        raise LinkExtractionError("Only publicly reachable http or https links can be extracted.")
    try:
        async with httpx.AsyncClient(timeout=12.0, follow_redirects=False) as client:
            response = await client.get(url, headers={"User-Agent": "JustDump/0.1"})
    except httpx.HTTPError as error:
        raise LinkExtractionError("The public link could not be reached.") from error
    if 300 <= response.status_code < 400:
        location = response.headers.get("location")
        if not location:
            raise LinkExtractionError("The public link redirected without a destination.")
        return await extract_public_article(str(response.url.join(location)))
    if response.status_code != 200 or "html" not in response.headers.get("content-type", "").lower():
        raise LinkExtractionError("The link did not provide a readable public HTML page.")
    if len(response.content) > MAX_BYTES:
        raise LinkExtractionError("The public page is too large to process.")
    soup = BeautifulSoup(response.content, "html.parser")
    for node in soup(["script", "style", "noscript", "svg"]):
        node.decompose()
    title = soup.title.get_text(" ", strip=True) if soup.title else ""
    text = " ".join(soup.get_text(" ", strip=True).split())[:MAX_TEXT_CHARS]
    if not text:
        raise LinkExtractionError("The public page did not contain readable text.")
    return f"{title}\n\n{text}".strip()
