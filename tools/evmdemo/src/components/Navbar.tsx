import { useEffect, useState, useContext } from "react";
import { Box, Flex, Text, Image } from "@chakra-ui/react";
import { fetchTokens } from "@/api/gateway";
import { useRouter } from "next/router";
import Lottie from "react-lottie";
import animationData from "@/assets/loading.json";

type NavbarProps = {
  activeHoldingId: string;
};
const Navbar = ({ activeHoldingId }: NavbarProps) => {
  const [balance, setBalance] = useState(0);
  const [symbol, setSymbol] = useState("");
  const [loading, setLoading] = useState(false);
  const router = useRouter();
  const getInfo = async () => {
    setLoading(true);
    // alert( activeHoldingId)
    const data = await fetchTokens(activeHoldingId);
    setBalance(data.balance);
    setSymbol(data.symbol);
    setLoading(false);
  };

  useEffect(() => {
    getInfo();
  }, [router.pathname, activeHoldingId]);
  return (
    <Flex
      bg="black"
      w="95vw"
      h="100px"
      p="10"
      m="10"
      marginRight={"2.5vw"}
      top="0"
      borderRadius="10"
      position="fixed"
      flexDirection={"row"}
      justifyContent={"space-between"}
      alignItems={"center"}
      zIndex={1}
      filter="drop-shadow(10px 10px 9px #000000);"
    >
      {/* Navber option for home */}
      <Box
        // bg="white"
        // borderRadius="30"
        p="10"
        m="10"
        // marginBottom="30"
        textAlign="center"
        fontFamily={"Roboto"}
      >
        <Image
          src="/logo.png"
          alt="logo"
          width={100}
          height={50}
          style={{ backgroundColor: "white", padding: 10, borderRadius: 10 }}
        />
      </Box>
      <Box
        bg="white"
        borderRadius="10"
        p="10"
        m="10"
        textAlign="center"
        fontFamily={"Roboto"}
      >
        {loading ? (
          <Lottie
            options={{
              loop: true,
              autoplay: true,
              animationData: animationData,
            }}
            height={75}
            width={75}
          />
        ) : (
          <Text fontFamily={"Roboto"} fontWeight={"bold"}>
            Corda Balance: {balance} {symbol}
          </Text>
        )}
      </Box>
      <Box
        bg="white"
        borderRadius="10"
        p="5"
        m="10"
        textAlign="center"
        fontFamily={"Roboto"}
      >
        <Text fontFamily={"Roboto"} fontWeight={"bold"}>
          EVM Explorer
        </Text>
      </Box>
    </Flex>
  );
};

export default Navbar;
