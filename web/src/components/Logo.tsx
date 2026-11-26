import { navigate } from '../router';
import { Mark } from './Mark';

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
        <Mark size={34} />
        Meld
      </a>
      {right}
    </header>
  );
}
