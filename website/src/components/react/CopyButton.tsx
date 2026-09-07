import { useEffect, useRef, useState } from 'react';

export default function CopyButton({ value, label = 'Copy' }: { value: string; label?: string }) {
  const [copied, setCopied] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => () => {
    if (timerRef.current !== null) clearTimeout(timerRef.current);
  }, []);

  return (
    <button
      type="button"
      className="syn-copy"
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(value);
          if (timerRef.current !== null) clearTimeout(timerRef.current);
          setCopied(true);
          timerRef.current = setTimeout(() => {
            setCopied(false);
            timerRef.current = null;
          }, 1500);
        } catch {
          setCopied(false);
        }
      }}
      aria-label={copied ? 'Copied' : 'Copy to clipboard'}
    >
      {copied ? 'Copied' : label}
    </button>
  );
}
