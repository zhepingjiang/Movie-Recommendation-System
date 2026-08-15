import { useRef, useState } from 'react';
import { useAuth } from '../hooks/useAuth';

/** Protected profile page at `/profile`: view account info, edit display name, upload an avatar. */
export default function ProfilePage() {
  const { user, refreshProfile, updateAvatar } = useAuth();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [displayName, setDisplayName] = useState(user?.displayName ?? '');
  const [savingName, setSavingName] = useState(false);
  const [nameError, setNameError] = useState<string | null>(null);
  const [nameSaved, setNameSaved] = useState(false);

  const [uploadingAvatar, setUploadingAvatar] = useState(false);
  const [avatarError, setAvatarError] = useState<string | null>(null);

  if (!user) {
    return null;
  }

  async function handleSaveDisplayName() {
    setSavingName(true);
    setNameError(null);
    setNameSaved(false);
    try {
      await refreshProfile({ displayName });
      setNameSaved(true);
    } catch {
      setNameError('Could not save your display name. Please try again.');
    } finally {
      setSavingName(false);
    }
  }

  async function handleAvatarChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploadingAvatar(true);
    setAvatarError(null);
    try {
      await updateAvatar(file);
    } catch {
      setAvatarError('Could not upload that image. Please use a JPEG, PNG, or WebP under 5MB.');
    } finally {
      setUploadingAvatar(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  }

  const inputClass =
    'w-full rounded-md border border-white/15 bg-white/[0.08] px-3.5 py-2.5 text-sm text-[#f2f2f2] focus:outline-none';
  const readOnlyInputClass =
    'w-full rounded-md border border-white/10 bg-white/[0.04] px-3.5 py-2.5 text-sm text-[#999]';

  return (
    <div className="mx-auto w-full max-w-[560px] px-6 py-10 pb-[60px]">
      <div className="mb-8 text-[28px] font-bold">Profile</div>

      <div className="mb-8 flex items-center gap-5">
        {user.avatarUrl ? (
          <img src={user.avatarUrl} alt="Avatar" className="h-20 w-20 rounded-full object-cover" />
        ) : (
          <div className="flex h-20 w-20 items-center justify-center rounded-full bg-[#333] text-2xl text-[#aaa]">
            {(user.displayName ?? user.username).charAt(0).toUpperCase()}
          </div>
        )}
        <div>
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            disabled={uploadingAvatar}
            className="cursor-pointer rounded-md border border-white/25 bg-white/10 px-4 py-2 text-sm text-[#f2f2f2] disabled:cursor-not-allowed disabled:opacity-60"
          >
            {uploadingAvatar ? 'Uploading…' : 'Change avatar'}
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            onChange={handleAvatarChange}
            className="hidden"
          />
          {avatarError && <div className="mt-2 text-sm text-[#e50914]">{avatarError}</div>}
        </div>
      </div>

      <div className="flex flex-col gap-4">
        <div>
          <div className="mb-1.5 text-xs text-[#999]">Username</div>
          <input className={readOnlyInputClass} value={user.username} readOnly />
        </div>

        <div>
          <div className="mb-1.5 text-xs text-[#999]">Email</div>
          <input className={readOnlyInputClass} value={user.email} readOnly />
        </div>

        <div>
          <div className="mb-1.5 text-xs text-[#999]">Display name</div>
          <input
            className={inputClass}
            value={displayName}
            onChange={(e) => {
              setDisplayName(e.target.value);
              setNameSaved(false);
            }}
            maxLength={150}
          />
        </div>

        {nameError && <div className="text-sm text-[#e50914]">{nameError}</div>}
        {nameSaved && <div className="text-sm text-[#4caf50]">Saved.</div>}

        <button
          type="button"
          onClick={handleSaveDisplayName}
          disabled={savingName || displayName === user.displayName}
          className="mt-2 w-fit cursor-pointer rounded-md bg-[#e50914] px-[26px] py-[11px] text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60"
        >
          {savingName ? 'Saving…' : 'Save changes'}
        </button>
      </div>
    </div>
  );
}
