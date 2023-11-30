import * as React from "react";
import { SVGProps } from "react";
const SvgComponent = (props: SVGProps<SVGSVGElement>) => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    width={800}
    height={800}
    fill="none"
    viewBox="0 0 24 24"
    {...props}
  >
    <path
      fill="white"
      fillRule="evenodd"
      d="m12 3.208-5.021 6.953L12 12.864l5.021-2.704L12 3.209Zm-.973-2.069a1.2 1.2 0 0 1 1.946 0l6.204 8.59a1.2 1.2 0 0 1-.404 1.76l-6.204 3.34a1.2 1.2 0 0 1-1.138 0l-6.204-3.34a1.2 1.2 0 0 1-.404-1.76l6.204-8.59Z"
      clipRule="evenodd"
    />
    <path
      fill="white"
      fillRule="evenodd"
      d="M17.71 13.527c1.16-.58 2.287.836 1.463 1.836l-6.247 7.585a1.2 1.2 0 0 1-1.852 0l-6.247-7.585c-.824-1 .304-2.416 1.463-1.836L12 16.382l5.71-2.855ZM16 16.5l-3.463 1.85a1.2 1.2 0 0 1-1.074 0L8 16.5l4 4.427 4-4.427Z"
      clipRule="evenodd"
    />
  </svg>
);
export default SvgComponent;
