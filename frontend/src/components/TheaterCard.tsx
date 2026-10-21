import type { NearbyTheater, Showtime } from '../types/theater';

// Every theater in the data set is in Ontario; show times in the theater's zone, not the
// browser's, so a traveller sees the time printed on the ticket.
const THEATER_TIME_ZONE = 'America/Toronto';

const timeFormatter = new Intl.DateTimeFormat('en-CA', {
  hour: 'numeric',
  minute: '2-digit',
  timeZone: THEATER_TIME_ZONE,
});
const dayKeyFormatter = new Intl.DateTimeFormat('en-CA', { timeZone: THEATER_TIME_ZONE });
const weekdayFormatter = new Intl.DateTimeFormat('en-CA', { weekday: 'long', timeZone: THEATER_TIME_ZONE });

const PREMIUM_FORMATS = new Set(['IMAX', '3D', 'ULTRAAVX']);

/** "Today", "Tomorrow" or the weekday name, for grouping a theater's showtimes by day. */
function dayLabel(startTime: Date, now: Date): string {
  const tomorrow = new Date(now.getTime() + 24 * 60 * 60 * 1000);
  const dayKey = dayKeyFormatter.format(startTime);
  if (dayKey === dayKeyFormatter.format(now)) return 'Today';
  if (dayKey === dayKeyFormatter.format(tomorrow)) return 'Tomorrow';
  return weekdayFormatter.format(startTime);
}

function groupByDay(showtimes: Showtime[]): [string, Showtime[]][] {
  const now = new Date();
  const showtimesByDay = new Map<string, Showtime[]>();
  for (const showtime of showtimes) {
    const label = dayLabel(new Date(showtime.startTime), now);
    showtimesByDay.set(label, [...(showtimesByDay.get(label) ?? []), showtime]);
  }
  return [...showtimesByDay.entries()];
}

interface TheaterCardProps {
  theater: NearbyTheater;
  /** 1-based position in the list; matches the pin number on the map. */
  listNumber: number;
  isSelected: boolean;
  onSelect: () => void;
}

/** One theater in the nearby list: name, address, distance and its upcoming showtimes. */
export default function TheaterCard({ theater, listNumber, isSelected, onSelect }: TheaterCardProps) {
  const addressLine = [theater.address, theater.city].filter(Boolean).join(', ');

  return (
    <article
      id={`theater-${theater.id}`}
      onClick={onSelect}
      className={`cursor-pointer rounded-lg border bg-[#1c1c22] p-4 transition-colors ${
        isSelected ? 'border-[#e50914]' : 'border-white/15 hover:border-white/25'
      }`}
    >
      <div className="flex justify-between gap-3">
        <div className="min-w-0">
          <h3 className="text-base font-semibold text-[#f2f2f2]">
            <span className="mr-2 text-[#999]">{listNumber}.</span>
            {theater.name}
          </h3>
          {addressLine && <div className="mt-0.5 text-[13px] text-[#999]">{addressLine}</div>}
          {theater.brand && (
            <span className="mt-2 inline-block rounded-full border border-white/15 bg-white/[0.06] px-2.5 py-0.5 text-[11px] text-[#d0d0d0]">
              {theater.brand}
            </span>
          )}
        </div>
        <div className="flex-none text-right">
          <span className="text-lg font-bold text-[#f2f2f2]">{theater.distanceKm.toFixed(1)}</span>
          <span className="text-sm text-[#999]"> km</span>
        </div>
      </div>

      <div className="mt-3 flex flex-col gap-2">
        {groupByDay(theater.showtimes).map(([label, showtimes]) => (
          <div key={label} className="flex flex-wrap items-center gap-1.5">
            <span className="w-[72px] flex-none text-xs text-[#999]">{label}</span>
            {showtimes.map((showtime) => (
              <button
                key={showtime.startTime + showtime.format}
                type="button"
                title="Booking isn't available yet"
                onClick={(event) => {
                  event.stopPropagation();
                  onSelect();
                }}
                className="cursor-pointer rounded-md border border-white/15 bg-white/[0.08] px-3 py-1.5 text-[13px] font-semibold text-[#f2f2f2] hover:border-[#e50914] hover:bg-[#e50914]"
              >
                {timeFormatter.format(new Date(showtime.startTime))}
                {PREMIUM_FORMATS.has(showtime.format) && (
                  <span className="ml-1.5 text-[10px] font-bold text-[#f5c518]">
                    {showtime.format === 'ULTRAAVX' ? 'UltraAVX' : showtime.format}
                  </span>
                )}
              </button>
            ))}
          </div>
        ))}
      </div>
    </article>
  );
}
