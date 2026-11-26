import { navigate } from '../router';

export function TopBar({ right }: { right?: React.ReactNode }) {
  return (
    <header className="topbar">
      <a
        className="brand"
        href="/"
        onClick={(e) => {
          e.preventDefault();
          navigate('/');
        }}
      >
        <img src="/icons/icon-192.png" alt="" width={32} height={32} />
        Meld
      </a>
      {right}
    </header>
  );
}
