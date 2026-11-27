// Build-time only (see vite.config.ts): renders the landing page to static HTML so search engines
// and link previewers see real content without running JavaScript, plus JSON-LD structured data.
import { renderToString } from 'react-dom/server';
import { faq, Landing } from './pages/Landing';

const SITE = 'https://meld-one.vercel.app';
const GITHUB = 'https://github.com/PrinceJoseph-software/meld';

export function renderLanding(): { html: string; jsonLd: string } {
  const html = renderToString(<Landing />);
  const graph = [
    {
      '@type': 'WebSite',
      '@id': `${SITE}/#website`,
      url: `${SITE}/`,
      name: 'Meld',
      description: 'Free group polls, ranked-choice voting and time-slot scheduling with no sign-up.',
      inLanguage: 'en',
    },
    {
      '@type': 'SoftwareApplication',
      '@id': `${SITE}/#app`,
      name: 'Meld',
      url: `${SITE}/`,
      applicationCategory: 'ProductivityApplication',
      operatingSystem: 'Android, Web',
      description:
        'Make a poll, share a link and decide together: ranked choice, time slots, live results and offline voting. No accounts, no ads.',
      image: `${SITE}/og.png`,
      screenshot: `${SITE}/media/hero-poster.jpg`,
      downloadUrl: `${GITHUB}/releases/latest`,
      installUrl: `${SITE}/app`,
      isAccessibleForFree: true,
      license: 'https://opensource.org/licenses/MIT',
      offers: { '@type': 'Offer', price: '0', priceCurrency: 'USD' },
      featureList: [
        'No sign-up or account',
        'Works offline and syncs later',
        'Ranked-choice (instant runoff) voting',
        'Time-slot scheduling with calendar export',
        'Slack and Discord /meld command',
        'Shareable result cards',
      ],
      sameAs: [GITHUB],
    },
    {
      '@type': 'FAQPage',
      '@id': `${SITE}/#faq`,
      mainEntity: faq().map((f) => ({
        '@type': 'Question',
        name: f.q,
        acceptedAnswer: { '@type': 'Answer', text: f.a },
      })),
    },
  ];
  const jsonLd = JSON.stringify({ '@context': 'https://schema.org', '@graph': graph }).replace(/</g, '\\u003c');
  return { html, jsonLd };
}
