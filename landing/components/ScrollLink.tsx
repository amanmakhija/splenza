"use client";

import { AnchorHTMLAttributes } from "react";

type ScrollLinkProps = AnchorHTMLAttributes<HTMLAnchorElement> & {
  href: `#${string}`;
};

export default function ScrollLink({
  href,
  onClick,
  children,
  ...rest
}: ScrollLinkProps) {
  function handleClick(e: React.MouseEvent<HTMLAnchorElement>) {
    e.preventDefault();
    const id = href.slice(1);
    document.getElementById(id)?.scrollIntoView({
      behavior: "smooth",
      block: "start",
    });
    onClick?.(e);
  }

  return (
    <a href={href} onClick={handleClick} {...rest}>
      {children}
    </a>
  );
}
