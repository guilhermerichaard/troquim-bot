# Troquim Premium Revenue Booking v1

Status: implementation contract
Date: 2026-11-08

## Product position

Troquim is not sold as "an AI chatbot" or as another scheduling calendar.

Troquim is the operational front desk for premium service businesses: it answers on WhatsApp, turns intent into valid bookings, protects calendar utilization, recovers cancellations and reduces repetitive front-desk work.

The initial ICP is a high-ticket or high-utilization beauty/wellness business with enough demand that missed messages, holes in the calendar, no-shows and poor rebooking have material financial cost.

## Architecture invariants

1. Domain decides. Language interprets.
2. Catalog is the only source for service identity, price and duration.
3. Availability is the only source for valid start times.
4. Appointment is the only source for a completed booking.
5. Conversation state carries only channel workflow state; it never becomes the booking ledger.
6. Presentation decides whether to use text, buttons, lists or WhatsApp Flow. It does not create business facts.
7. A richer AI interpreter may replace heuristics, but it must emit structured interpretation and cannot confirm availability, price or booking.
8. Unknown language must never reset the workflow or replay the home menu by default.
9. Every automated commercial action must be attributable to a business outcome before it is exposed as a revenue metric.

## Target customer experience

### Greeting

Do not show the main menu on a plain greeting.

Customer:
> oi

Troquim:
> Boa noite, Gui. Como posso ajudar?

No repeated greeting in the same active conversation.

### Booking from a button

Customer taps "Agendar".

Troquim:
> O que você quer fazer?

The WhatsApp list shows canonical catalog data:
- service title
- real duration
- real price when published

The visual agenda is not opened at this point.

### Booking from natural language

Customer:
> quero escova amanhã depois das 15

Interpretation may infer:
- intent = booking
- service query = escova
- date preference = tomorrow
- time preference = after 15:00

Domain resolves:
- canonical service
- duration
- capable professional(s)
- real availability

Presentation returns only the best valid choices:
> Amanhã depois das 15 tenho:
> [15:30] [17:00] [18:30]
> [Ver mais horários]

The customer is never asked again for facts already resolved.

### Unknown language

Customer:
> oxe

The system must not:
- reset state;
- repeat the greeting;
- repeat the main menu.

If no structured interpretation is possible, the channel abstains and allows the conversational fallback to respond using the existing state.

## Slot policy

Service duration and start-time granularity are separate concepts.

- Service duration comes exclusively from the catalog.
- Candidate start times come exclusively from the availability domain.
- A 15-minute candidate step does not mean a 15-minute service.
- The client should not be shown the entire mathematical grid.

The channel should normally show at most 3 recommended starts. Recommendation is a ranking of already-valid slots. It may consider:
1. explicit customer time preference;
2. smallest adjacent calendar gap;
3. earliest valid date;
4. stable tie-breaker.

"See more" expands the choice surface. It must never manufacture availability.

## Professional selection

Asking for a professional is optional unless the customer explicitly cares.

When multiple professionals can perform the service, selection must be an explicit policy, not "first list element".

Default v1 policy for "any professional":
1. only capable professionals;
2. only real availability;
3. prefer the professional/slot that creates the smallest adjacent gap;
4. stable deterministic tie-breaker.

The selected professional must be persisted in the conversation draft and revalidated at confirmation.

## Revenue automations

The repository already contains canonical building blocks for:
- waitlist;
- released-slot notification;
- reminders;
- reminder confirmation/cancellation/reschedule;
- upsell rules;
- slot ranking.

v1 must compose these; it must not reimplement them in the conversation layer.

### Waitlist
When requested availability is absent:
- offer closest valid alternatives;
- offer waitlist only when a compatible request can be represented;
- when a slot is released, canonical waitlist service chooses/notifies eligible entries.

### Reminder
Reminder presents actionable choices:
- Confirm
- Reschedule
- Cancel

Cancellation inside policy releases the slot and triggers the existing waitlist path.

### Upsell
Only offer an add-on when:
- rule is active;
- service exists in catalog;
- policy allows upsell;
- the add-on really fits after the base service;
- price comes from catalog.

## Premium UX rules

1. One meaningful question at a time.
2. Buttons/lists only when a concrete choice exists.
3. Never make the customer choose how to use the product ("chat or agenda?") before choosing what they want.
4. Never repeat a greeting or menu as error recovery.
5. Never expose internal 15-minute grids as if each row were a different service duration.
6. Prefer 3 useful options to 20 technically valid ones.
7. No fake human handoff.
8. No fake facts.
9. No fake revenue claims.
10. Language should be concise, calm and service-oriented; no "AI personality theater".

## Evals required before release

Conversation evals are multi-turn scenarios, not single response assertions.

Required suites:
- greeting -> unexpected slang;
- direct service -> day -> time;
- service + day + period in one message;
- price/address/hours from canonical sources;
- ambiguous typo and explicit correction;
- occupied requested time -> 3 alternatives;
- multiple capable professionals -> deterministic valid assignment;
- "more times" does not reset service/day;
- back navigation;
- confirmation race: slot taken between display and confirmation;
- reminder confirm/reschedule/cancel;
- waitlist slot release;
- upsell that fits / does not fit;
- completed booking -> query/cancel;
- unknown conversational message never replays the home menu.

## Commercial proof

Do not sell counts of AI messages.

Owner-facing business metrics should eventually be computed from events that have real domain evidence:
- bookings created by Troquim;
- bookings confirmed after reminder;
- cancellations recovered by waitlist;
- add-on bookings generated by upsell;
- rebookings attributable to automation;
- estimated booked value only when catalog price exists.

Until attribution is implemented, the landing page must describe capabilities rather than fabricate ROI.

## Landing-page contract

Marketing lives in the separate public repository. Owner console remains the authenticated product.

Positioning:
- premium operational concierge;
- WhatsApp-native;
- result and client experience first;
- AI is supporting technology, not the headline.

Visual direction:
- editorial quiet-luxury;
- near-black / warm off-white / restrained warm accent;
- strong typography and whitespace;
- real product mockups;
- no neon AI gradients, robot imagery, random glass cards or fake dashboards.

Primary CTA:
- "Solicitar uma apresentação"

Secondary CTA:
- product walkthrough.

No fake client logos, fake testimonials, fake revenue counters or fake case studies.
