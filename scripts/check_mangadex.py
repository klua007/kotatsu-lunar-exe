#!/usr/bin/env python3
"""
Read-only verification of the MangaDex source against the live public API.

It reproduces, byte for byte, the query strings that
``desktop/src/main/kotlin/.../sources/mangadex/MangaDexSource.kt`` builds and checks that every
endpoint answers. It is deliberately boring: **GET only, no authentication, no cookies, no
download written to disk**, and it stops at the first page image (the bytes are counted and
dropped).

Why it exists: the HTTP 400 of 0.2.0 was a pure query-string mistake
(``contentRating[]=porn`` instead of the enumerated ``pornographic``, and ``status=`` instead of
``status[]=``). The offline test suite pins the parameters, this script additionally proves that
the real API accepts them today.

Usage:
    python3 scripts/check_mangadex.py                 # https://api.mangadex.org
    python3 scripts/check_mangadex.py --base https://api.mangadex.dev
    python3 scripts/check_mangadex.py --json          # machine readable

Exit code: 0 when every check passed, 1 otherwise. Never raises on a network problem: a failed
check is *reported*, which is what a human needs to read.
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.parse
import urllib.request

DEFAULT_BASE = "https://api.mangadex.org"
TIMEOUT_SECONDS = 20
USER_AGENT = "KotatsuLunar-readonly-check (+https://github.com/klua007/kotatsu-lunar-exe)"

# Mirrors net/urlWithQuery.encodeQueryValue
_SAFE = set(
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"
)


def encode(value: str) -> str:
    out = []
    for char in value:
        if char in _SAFE:
            out.append(char)
        elif char == " ":
            out.append("%20")
        else:
            out.extend("%%%02X" % byte for byte in char.encode("utf-8"))
    return "".join(out)


def url(base: str, path: str, params: list[tuple[str, str]]) -> str:
    query = "&".join("%s=%s" % (encode(k), encode(v)) for k, v in params if v)
    return base + path + ("?" + query if query else "")


INCLUDES = [("includes[]", "cover_art"), ("includes[]", "author"), ("includes[]", "artist")]
LANGUAGES = ("en", "pt-br")


def content_ratings(include_adult: bool = False) -> list[tuple[str, str]]:
    ratings = ["safe", "suggestive"]
    if include_adult:
        # "pornographic" is the enumerated value; "porn" is rejected with HTTP 400.
        ratings += ["erotica", "pornographic"]
    return [("contentRating[]", value) for value in ratings]


def check(name: str, method: str, target: str, expect_status: int, want_json: bool = True):
    """Runs one request and returns a result dict. Never throws."""
    request = urllib.request.Request(target, method=method)
    request.add_header("User-Agent", USER_AGENT)
    request.add_header("Accept", "application/json")
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            body = response.read()
            status = response.status
            content_type = response.headers.get("Content-Type", "")
    except urllib.error.HTTPError as error:
        body = error.read()
        status = error.code
        content_type = error.headers.get("Content-Type", "") if error.headers else ""
    except Exception as error:  # network, DNS, TLS, timeout — all reported, none raised
        return {
            "check": name, "ok": False, "status": None,
            "detail": "%s: %s" % (type(error).__name__, error), "url": target,
        }

    ok = status == expect_status
    detail = "HTTP %d" % status
    payload = None
    if want_json and body[:1] in (b"{", b"["):
        try:
            payload = json.loads(body.decode("utf-8"))
        except ValueError:
            ok = False
            detail += " (body is not JSON)"
    elif want_json:
        ok = False
        detail += " (unexpected content type %s)" % (content_type or "unknown")

    if payload is not None:
        result = payload.get("result")
        if expect_status == 200 and result != "ok":
            ok = False
            detail += " · result=%s" % result
            errors = payload.get("errors") or []
            if errors:
                detail += " · " + str(errors[0].get("detail") or errors[0].get("title"))
        if expect_status >= 400:
            detail += " · " + str(
                (payload.get("errors") or [{}])[0].get("title") or "rejected as expected"
            )
    if not want_json:
        detail += " · %d bytes, %s" % (len(body), content_type or "no content type")
    return {"check": name, "ok": ok, "status": status, "detail": detail, "url": target}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default=DEFAULT_BASE, help="API base URL")
    parser.add_argument("--title", default="solo leveling", help="title used by the search checks")
    parser.add_argument("--json", action="store_true", help="print JSON instead of a table")
    args = parser.parse_args()
    base = args.base.rstrip("/")

    results = []

    # 1. browse ("Popular now")
    results.append(check(
        "browse / popular",
        "GET",
        url(base, "/manga", [
            ("limit", "5"), ("offset", "0"),
            *INCLUDES,
            *content_ratings(),
            ("order[followedCount]", "desc"),
            ("hasAvailableChapters", "true"),
        ]),
        200,
    ))

    # 2. search by title
    search_url = url(base, "/manga", [
        ("limit", "5"), ("offset", "0"),
        *INCLUDES,
        *content_ratings(),
        ("title", args.title),
        ("order[relevance]", "desc"),
        ("hasAvailableChapters", "true"),
    ])
    results.append(check("search by title", "GET", search_url, 200))

    # 3. search with the adult content ratings (the parameter that used to be rejected)
    results.append(check(
        "search with contentRating[]=pornographic",
        "GET",
        url(base, "/manga", [
            ("limit", "1"), ("offset", "0"),
            *content_ratings(include_adult=True),
            ("title", args.title),
            ("order[relevance]", "desc"),
        ]),
        200,
    ))

    # 4. search with a publication status
    results.append(check(
        "search with status[]=completed",
        "GET",
        url(base, "/manga", [
            ("limit", "1"), ("offset", "0"),
            *content_ratings(),
            ("title", args.title),
            ("order[relevance]", "desc"),
            ("status[]", "completed"),
        ]),
        200,
    ))

    # 5/6/7 need a real manga: take the first hit of the search above.
    manga_id = None
    try:
        with urllib.request.urlopen(
            urllib.request.Request(search_url, headers={"User-Agent": USER_AGENT}),
            timeout=TIMEOUT_SECONDS,
        ) as response:
            data = json.loads(response.read().decode("utf-8"))
        manga_id = (data.get("data") or [{}])[0].get("id")
    except Exception:
        manga_id = None

    if not manga_id:
        results.append({
            "check": "details / feed / pages", "ok": False, "status": None,
            "detail": "skipped: the search returned no title, so nothing could be opened",
            "url": search_url,
        })
    else:
        results.append(check(
            "details of a title",
            "GET",
            url(base, "/manga/" + manga_id, INCLUDES),
            200,
        ))

        feed_url = url(base, "/manga/%s/feed" % manga_id, [
            ("limit", "500"), ("offset", "0"),
            ("order[volume]", "asc"), ("order[chapter]", "asc"),
            ("includes[]", "scanlation_group"),
            ("includeExternalUrl", "0"),
            *[("translatedLanguage[]", lang) for lang in LANGUAGES],
            *content_ratings(include_adult=True),
        ])
        results.append(check("chapter feed (all content ratings)", "GET", feed_url, 200))

        chapter_id = None
        try:
            with urllib.request.urlopen(
                urllib.request.Request(feed_url, headers={"User-Agent": USER_AGENT}),
                timeout=TIMEOUT_SECONDS,
            ) as response:
                feed = json.loads(response.read().decode("utf-8"))
            for entity in feed.get("data") or []:
                attributes = entity.get("attributes") or {}
                if attributes.get("externalUrl") or attributes.get("isUnavailable"):
                    continue
                chapter_id = entity.get("id")
                break
        except Exception:
            chapter_id = None

        if not chapter_id:
            results.append({
                "check": "page list / first page image", "ok": False, "status": None,
                "detail": "skipped: no readable chapter in %s" % ", ".join(LANGUAGES),
                "url": feed_url,
            })
        else:
            at_home_url = url(base, "/at-home/server/" + chapter_id, [])
            results.append(check("page list (at-home server)", "GET", at_home_url, 200))
            try:
                with urllib.request.urlopen(
                    urllib.request.Request(at_home_url, headers={"User-Agent": USER_AGENT}),
                    timeout=TIMEOUT_SECONDS,
                ) as response:
                    at_home = json.loads(response.read().decode("utf-8"))
                file_name = ((at_home.get("chapter") or {}).get("data") or [None])[0]
                image_url = "%s/data/%s/%s" % (
                    at_home.get("baseUrl", ""), at_home.get("chapter", {}).get("hash", ""), file_name,
                )
                if file_name:
                    results.append(check(
                        "first page image (bytes counted, never saved)",
                        "GET", image_url, 200, want_json=False,
                    ))
                else:
                    results.append({
                        "check": "first page image", "ok": False, "status": None,
                        "detail": "the chapter exposes no page", "url": at_home_url,
                    })
            except Exception as error:
                results.append({
                    "check": "page list / first page image", "ok": False, "status": None,
                    "detail": "%s: %s" % (type(error).__name__, error), "url": at_home_url,
                })

    # 8. Regression guards: the requests 0.2.0 used to send must still be rejected, so nobody
    #    "simplifies" the enumeration back. Read-only and harmless.
    results.append(check(
        "regression: contentRating[]=porn is still rejected",
        "GET",
        url(base, "/manga", [("limit", "1"), *content_ratings(include_adult=True)[:-1],
                             ("contentRating[]", "porn")]),
        400,
    ))
    results.append(check(
        "regression: a scalar status is still rejected",
        "GET",
        url(base, "/manga", [("limit", "1"), ("status", "ongoing")]),
        400,
    ))
    results.append(check(
        "regression: offset beyond the result window is still rejected",
        "GET",
        url(base, "/manga", [("limit", "1"), ("offset", "10000")]),
        400,
    ))

    passed = sum(1 for item in results if item["ok"])
    if args.json:
        print(json.dumps({"base": base, "passed": passed, "total": len(results),
                          "results": results}, indent=2))
    else:
        print("MangaDex read-only check — %s" % base)
        print("-" * 96)
        for item in results:
            print("%-4s %-46s %s" % (
                "PASS" if item["ok"] else "FAIL", item["check"], item["detail"],
            ))
        print("-" * 96)
        print("%d/%d checks passed" % (passed, len(results)))
    return 0 if passed == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
