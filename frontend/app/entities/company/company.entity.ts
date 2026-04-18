export interface CompanyEntity {
  id: string
  name: string
  domain: string | null
  industry: string | null
  companySize: string | null
  annualRevenue: string | null
  description: string | null
  website: string | null
  logoUrl: string | null
  phone: string | null
  address: string | null
  city: string | null
  state: string | null
  country: string | null
  zipCode: string | null
  contactCount: number
  createdAt: Date
  updatedAt: Date
}
