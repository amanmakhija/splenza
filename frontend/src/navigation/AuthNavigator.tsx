import React from "react";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { AuthStackParamList } from "./types";
import { LoginScreen } from "@/screens/auth/LoginScreen";
import { SignupScreen } from "@/screens/auth/SignupScreen";
import { ForgotPasswordScreen } from "@/screens/auth/ForgotPasswordScreen";
import { EmailSentScreen } from "@/screens/auth/EmailSentScreen";
import { VerifyEmailScreen } from "@/screens/auth/VerifyEmailScreen";
import { ResetPasswordScreen } from "@/screens/auth/ResetPasswordScreen";
import { PasswordResetSuccessScreen } from "@/screens/auth/PasswordResetSuccessScreen";
import { VerificationSuccessScreen } from "@/screens/auth/VerificationSuccessScreen";
import { ChangeEmailScreen } from "@/screens/auth/ChangeEmailScreen";

const Stack = createNativeStackNavigator<AuthStackParamList>();

export function AuthNavigator() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="Login" component={LoginScreen} />
      <Stack.Screen name="Signup" component={SignupScreen} />
      <Stack.Screen name="ForgotPassword" component={ForgotPasswordScreen} />
      <Stack.Screen name="VerifyEmail" component={VerifyEmailScreen} />
      <Stack.Screen name="ResetPassword" component={ResetPasswordScreen} />
      <Stack.Screen name="EmailSent" component={EmailSentScreen} />
      <Stack.Screen
        name="PasswordResetSuccess"
        component={PasswordResetSuccessScreen}
      />
      <Stack.Screen
        name="VerificationSuccess"
        component={VerificationSuccessScreen}
      />
      <Stack.Screen name="ChangeEmail" component={ChangeEmailScreen} />
    </Stack.Navigator>
  );
}
