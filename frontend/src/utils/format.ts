export function releaseYear(releaseDate: string | null): string {
  return releaseDate ? releaseDate.slice(0, 4) : 'TBA';
}

export function formatRuntime(minutes: number | null): string | null {
  if (minutes == null) return null;
  const hours = Math.floor(minutes / 60);
  const mins = minutes % 60;
  return `${hours}h ${mins}m`;
}
