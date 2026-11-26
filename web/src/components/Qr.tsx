import { useEffect, useState } from 'react';
import QRCode from 'qrcode';

export function Qr({ value, label }: { value: string; label: string }) {
  const [src, setSrc] = useState<string | null>(null);
  useEffect(() => {
    QRCode.toDataURL(value, { margin: 0, width: 312, errorCorrectionLevel: 'M' })
      .then(setSrc)
      .catch(() => setSrc(null));
  }, [value]);
  return <div className="qr">{src && <img src={src} alt={label} />}</div>;
}
