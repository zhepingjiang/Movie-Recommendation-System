import { useState } from 'react';
import { rateMovie } from '../api/movies';

interface RatingModalProps {
  movieId: number;
  movieTitle: string;
  onClose: () => void;
  onRated: (score: number) => void;
}

const SCORES = [1, 2, 3, 4, 5];

/** Popup for submitting a 1-5 star rating for a movie, opened from MovieDetailPage. */
export default function RatingModal({ movieId, movieTitle, onClose, onRated }: RatingModalProps) {
  const [selected, setSelected] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit() {
    if (selected === null) return;
    setSubmitting(true);
    setError(null);
    try {
      const rating = await rateMovie(movieId, selected);
      onRated(rating.score);
    } catch {
      setError('Could not save your rating. Please try again.');
      setSubmitting(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 px-4" onClick={onClose}>
      <div
        className="w-full max-w-sm rounded-lg border border-white/15 bg-[#1c1c22] p-6"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-1 text-lg font-semibold">Rate this movie</div>
        <div className="mb-5 truncate text-sm text-[#999]">{movieTitle}</div>

        <div className="mb-5 flex justify-center gap-2">
          {SCORES.map((score) => (
            <button
              key={score}
              type="button"
              onClick={() => setSelected(score)}
              aria-label={`${score} star${score > 1 ? 's' : ''}`}
              className={`cursor-pointer text-4xl leading-none ${
                selected !== null && score <= selected ? 'text-[#f5c518]' : 'text-white/25'
              }`}
            >
              ★
            </button>
          ))}
        </div>

        {error && <div className="mb-4 text-center text-sm text-[#e50914]">{error}</div>}

        <div className="flex justify-end gap-3">
          <button
            type="button"
            onClick={onClose}
            className="cursor-pointer rounded-md border border-white/25 bg-white/10 px-5 py-2 text-sm font-semibold text-white"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            disabled={selected === null || submitting}
            className="cursor-pointer rounded-md border-none bg-[#e50914] px-5 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50"
          >
            {submitting ? 'Submitting…' : 'Submit'}
          </button>
        </div>
      </div>
    </div>
  );
}
