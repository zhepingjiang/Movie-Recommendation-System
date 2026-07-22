export default function Footer() {
  return (
    <div className="border-t border-white/[0.08] py-10 pb-[50px]">
      <div className="mx-auto w-full max-w-[1400px] px-[60px]">
        <div className="mb-3.5 flex justify-center gap-6">
          <a href="#" className="text-[13px] text-[#999] no-underline hover:text-[#ccc]">
            Privacy policy
          </a>
          <a href="#" className="text-[13px] text-[#999] no-underline hover:text-[#ccc]">
            Terms of service
          </a>
          <a href="#" className="text-[13px] text-[#999] no-underline hover:text-[#ccc]">
            Contact us
          </a>
        </div>
        <div className="text-center text-xs text-[#666]">© 2026 CineMind. All rights reserved.</div>
      </div>
    </div>
  );
}
