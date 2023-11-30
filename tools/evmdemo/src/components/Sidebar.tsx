import React from "react";
import { Box } from "@chakra-ui/react";
import Home from "@/svg/Home";
import Participants from "@/svg/Participants";
import Settings from "@/svg/Settings";
import Link from "next/link";
import Ethereum from "@/svg/Ethereum";

type SidebarProps = {
  setOpenNetworkParticipants: Function;
};

const Sidebar = ({ setOpenNetworkParticipants }: SidebarProps) => {
  return (
    <Box bg="#EC1D24" w="300px" h="100vh" p="10" m="10" borderRadius="30">
      <Link
        href="/assets"
        style={{
          marginLeft: 15,
          textDecoration: "none",
          fontFamily: "Roboto",
          color: "white",
          fontSize: 20,
          display: "flex",
          alignItems: "center",
        }}
      >
        <Home height={20} width={20} fill="white" />
        <h3
          style={{
            textDecoration: "none",
            marginLeft: 10,
            cursor: "pointer",
            fontSize: 17,
          }}
        >
          Home
        </h3>
      </Link>
      <span
        onClick={() => {
          setOpenNetworkParticipants(true);
        }}
        style={{
          marginLeft: 15,
          textDecoration: "none",
          fontFamily: "Roboto",
          color: "white",
          fontSize: 20,
          display: "flex",
          alignItems: "center",
        }}
      >
        <Participants height={20} width={20} fill="white" />
        <h3
          style={{
            textDecoration: "none",
            marginLeft: 10,
            cursor: "pointer",
            fontSize: 17,
          }}
        >
          Network Participants
        </h3>
      </span>

      <Link
        href="http://localhost:5173"
        target="_"
        style={{
          marginLeft: 15,
          textDecoration: "none",
          fontFamily: "Roboto",
          color: "white",
          fontSize: 20,
          display: "flex",
          alignItems: "center",
        }}
      >
        <Ethereum height={20} width={20} fill="white" />
        <h3
          style={{
            textDecoration: "none",
            marginLeft: 10,
            cursor: "pointer",
            fontSize: 17,
          }}
        >
          Block Explorer
        </h3>
      </Link>
      <span
        onClick={() => {
          setOpenNetworkParticipants(true);
        }}
        style={{
          marginLeft: 15,
          textDecoration: "none",
          fontFamily: "Roboto",
          color: "white",
          fontSize: 20,
          display: "flex",
          alignItems: "center",
        }}
      >
        <Settings height={20} width={20} fill="white" />
        <h3
          style={{
            textDecoration: "none",
            marginLeft: 10,
            cursor: "pointer",
            fontSize: 17,
          }}
        >
          Settings
        </h3>
      </span>
    </Box>
  );
};

export default Sidebar;
