import { Link } from 'react-router-dom';

interface OnboardingHeaderProps {
  currentStep: 1 | 2 | 3;
}

const STEPS = [
  { step: 1, label: 'Create your account' },
  { step: 2, label: 'Pick your genres' },
  { step: 3, label: 'Start exploring' },
] as const;

/** Logo + 3-step progress indicator shared by the /register onboarding pages. */
export default function OnboardingHeader({ currentStep }: OnboardingHeaderProps) {
  return (
    <header className="flex items-center justify-between px-6 py-5 sm:px-12">
      <Link to="/" className="text-lg font-extrabold tracking-wide text-[#e50914] no-underline">
        CINEMIND
      </Link>
      <div className="flex items-center gap-2" aria-label="Onboarding progress">
        {STEPS.map(({ step, label }, i) => (
          <div key={step} className="flex items-center gap-2">
            {i > 0 && <div className="h-px w-9 bg-[#262626]" />}
            <div
              title={`Step ${step}: ${label}`}
              className={`flex h-[26px] w-[26px] items-center justify-center rounded-full border text-xs ${
                step < currentStep
                  ? 'border-[#e50914] bg-[#e50914] text-white'
                  : step === currentStep
                    ? 'border-[#e50914] bg-[#e50914]/20 text-[#f5f5f5]'
                    : 'border-[#262626] text-[#a3a3a3]'
              }`}
            >
              {step < currentStep ? '✓' : step}
            </div>
          </div>
        ))}
      </div>
    </header>
  );
}
