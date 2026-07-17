// lib/google.ts

import { GoogleSignin } from "@react-native-google-signin/google-signin";
import Constants from "expo-constants";

GoogleSignin.configure({
  webClientId: Constants.expoConfig?.extra?.googleWebClientId,
  offlineAccess: false,
});
