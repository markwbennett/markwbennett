# Per incuriam in Texas: what I could and could not check

## What this session could actually do

Outbound network here is restricted to GitHub. No Westlaw, no Lexis, no fetching
opinion text — not even from txcourts.gov or CourtListener, which are blocked at
the proxy. Everything below comes from **web-search result summaries**, which
means: treat every citation as a lead to verify, not as verified law. I have not
read any of these opinions.

## Finding 1: no Texas use of "per incuriam" surfaced

Several searches, including ones restricted to txcourts.gov, courtlistener.com
and law.justia.com, produced **no Texas Court of Criminal Appeals or Texas court
of appeals opinion using the phrase**. The hits were all dictionaries, the
Wikipedia entry, English and Indian cases, and — repeatedly — American discussion
of *per curiam*, which search engines conflate with it.

That is a weak negative. Web search is not full-text case search, and a phrase
buried in a 1987 opinion would never surface this way. **The real test is a
full-text query you can run in a minute:**

```
Westlaw:  "per incuriam"           (database: TX-CS, then TX-CS-CRIM)
Lexis:    "per incuriam"           (Texas Cases, All)
Free:     scholar.google.com > Case law > Texas > "per incuriam"
```

If that comes back empty or near-empty in Texas, that is itself the finding — and
it is the interesting one.

## Finding 2: Texas already has the doorway, under a different name

The more useful result. The CCA's own stare decisis framework reportedly includes
a factor for precedent that was **"flawed from the outset."** Search summaries
attribute the three-factor articulation to:

> *Hammock v. State*, 46 S.W.3d 889, 892-93 (Tex. Crim. App. 2001)

with the factors given as: (1) the original rule was flawed from the outset;
(2) the older precedent conflicts with a newer, more soundly reasoned decision;
(3) the rule consistently produces unjust results or places unnecessary burdens
on the system. Summaries also attach similar factors to *Ex parte Lewis*.

**Verification status:** I confirmed that *Hammock v. State*, 46 S.W.3d 889, is a
real May 23, 2001 CCA decision (it granted PDR on whether a limiting instruction
must be requested when the evidence comes in, and reaffirmed *Garcia v. State*,
887 S.W.2d 862 (Tex. Crim. App. 1994)). That fits — a court declining to overrule
its own precedent is exactly where it would set out when overruling *is* proper.
But **I have not read 892-93 and cannot confirm the quoted factors or the pin
cite.** Read it before it goes in a brief.

One more unverified thread worth chasing: the summaries also say the burden is
heavier where the court is asked to overrule a point of **statutory
construction** than a judge-made rule. If true, it decides which of these fights
are worth taking.

## Finding 3: the strategy this suggests

You do not need to import *per incuriam* by name, and probably should not lead
with it. Texas supplies the doorway — "flawed from the outset" — and the English
doctrine supplies the **mechanism** that explains *why* a rule can be flawed from
the outset: the original court was never shown the statute or the controlling
decision, so it never confronted the question at all.

That is a two-step argument: a Texas factor the CCA already recognizes, plus a
reason it applies here that nobody has articulated, with *Young v Bristol
Aeroplane Co Ltd* [1944] KB 718 and the Commonwealth line available as persuasive
reasoning rather than authority. It is not a stretch and it is not a gimmick —
it is an explanation for the factor the court already applies.

## The queries that actually find fifty-year gaps

More valuable than hunting the Latin. These find the places where the CCA has
repeatedly declined to decide something:

```
"assuming without deciding"      /p  <your issue>
"we need not decide"             /p  <your issue>
"assuming arguendo"              /p  <your issue>
"has never squarely"  or  "not squarely addressed"
"without the benefit of"  /s  brief!
"sub silentio"           /s  precedent
"flawed from the outset"                 -> the whole CCA stare decisis line
```

The first three are the mechanical version of your thesis: every "assuming
without deciding" in a line of cases is a question the court stepped over, and if
the step-over has been repeated for decades, the underlying question has never
been briefed properly to anyone.

A citator pass does the other half: take the foundational case in a line, pull
everything citing it, and look for the tell — the original decided the point in a
sentence with no reasoning, and everything since cites the original rather than
reasoning independently. That is a per incuriam pattern whether or not Texas has
ever used the phrase.
